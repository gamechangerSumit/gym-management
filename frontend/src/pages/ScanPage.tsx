
import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import jsQR from 'jsqr';
import { get, post, qs } from '../api/client';
import type { MemberSummary, Page, ScanResponse } from '../api/types';
import { formatDate } from '../lib/format';
import { Empty, ErrorBox, FormError, PageHeader, Spinner, useDebounced } from '../components/ui';
import { CheckCircle2, Keyboard, QrCode, RefreshCw, ScanLine, XCircle } from 'lucide-react';

const PREFIX = 'GYMPASS:';
const RESULT_TIMEOUT = 2500;

function ResultCard({
  r,
  onDismiss,
}: {
  r: ScanResponse;
  onDismiss: () => void;
}) {
  const ok = r.result === 'CHECKED_IN';
  const warn = r.result === 'DUPLICATE_RECENT';

  return (
    <div
      className={`scan-result ${ok ? 'ok' : warn ? 'warn' : 'bad'}`}
      role="status"
      aria-live="polite"
    >
      <div className="scan-result-icon">{ok ? <CheckCircle2 size={28}/> : warn ? <RefreshCw size={28}/> : <XCircle size={28}/>}</div>
      <p className="big">{ok ? 'Checked in' : warn ? 'Already checked in' : 'Not allowed'}</p>

      {r.memberName && (
        <p>
          <strong>{r.memberName}</strong>{' '}
          {r.memberCode && <span>({r.memberCode})</span>}
        </p>
      )}

      <p>{r.message}</p>

      {r.membershipEndDate && (
        <p className="small-print">
          Membership ends {formatDate(r.membershipEndDate)}
        </p>
      )}

      <p className="small-print">
        Scanner will resume automatically.
      </p>

      <button className="btn" onClick={onDismiss}>
        Next
      </button>
    </div>
  );
}

export function ScanPage() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  // Lock prevents multiple requests for the same visible QR.
  const lockRef = useRef(false);
  const inFlightRef = useRef(false);
  const resultRef = useRef<ScanResponse | null>(null);

  const [camera, setCamera] = useState<
    'starting' | 'on' | 'denied' | 'unsupported'
  >('starting');

  const [cameraMessage, setCameraMessage] = useState('');
  const [cameraAttempt, setCameraAttempt] = useState(0);
  const [result, setResult] = useState<ScanResponse | null>(null);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);

  const scan = useMutation({
    mutationFn: (token: string) =>
      post<ScanResponse>('/api/v1/attendance/scan', { token }),

    onSuccess: (response) => {
      inFlightRef.current = false;
      resultRef.current = response;
      setResult(response);
    },

    onError: () => {
      inFlightRef.current = false;
      // Keep the QR lock until the QR leaves the frame.
      // This prevents immediate repeated requests for the same code.
    },
  });

  const dismissResult = useCallback(() => {
    resultRef.current = null;
    setResult(null);
  }, []);

  // Auto-clear result for both QR and manual check-in.
  useEffect(() => {
    resultRef.current = result;

    if (!result) return;

    const timer = window.setTimeout(() => {
      resultRef.current = null;
      setResult(null);
    }, RESULT_TIMEOUT);

    return () => window.clearTimeout(timer);
  }, [result]);

  useEffect(() => {
    let raf = 0;
    let cancelled = false;

    async function startCamera() {
      setCamera('starting');
      setCameraMessage('');

      if (!navigator.mediaDevices?.getUserMedia) {
        setCamera('unsupported');
        setCameraMessage(
          'Camera access requires HTTPS or localhost and a supported browser.',
        );
        return;
      }

      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: {
            facingMode: { ideal: 'environment' },
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
        });

        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }

        streamRef.current = stream;

        const video = videoRef.current;

        if (!video) {
          throw new Error('Camera preview element is unavailable.');
        }

        video.srcObject = stream;
        video.muted = true;
        video.playsInline = true;

        await video.play();

        if (cancelled) {
          stop();
          return;
        }

        setCamera('on');

        const tick = () => {
          if (cancelled) return;

          const videoElement = videoRef.current;
          const canvas = canvasRef.current;
          let qrFound = false;

          if (
            videoElement &&
            canvas &&
            videoElement.readyState >=
              HTMLMediaElement.HAVE_CURRENT_DATA &&
            videoElement.videoWidth > 0 &&
            videoElement.videoHeight > 0
          ) {
            canvas.width = videoElement.videoWidth;
            canvas.height = videoElement.videoHeight;

            const context = canvas.getContext('2d', {
              willReadFrequently: true,
            });

            if (context) {
              context.drawImage(
                videoElement,
                0,
                0,
                canvas.width,
                canvas.height,
              );

              const frame = context.getImageData(
                0,
                0,
                canvas.width,
                canvas.height,
              );

              const code = jsQR(
                frame.data,
                frame.width,
                frame.height,
                { inversionAttempts: 'dontInvert' },
              );

              if (code?.data.startsWith(PREFIX)) {
                qrFound = true;

                if (
                  !lockRef.current &&
                  !inFlightRef.current &&
                  !resultRef.current
                ) {
                  lockRef.current = true;
                  inFlightRef.current = true;
                  scan.mutate(code.data.slice(PREFIX.length));
                }
              }
            }
          }

          // Rearm only when the previous QR is no longer visible
          // and no request/result is being processed.
          if (
            !qrFound &&
            !inFlightRef.current &&
            !resultRef.current
          ) {
            lockRef.current = false;
          }

          raf = window.requestAnimationFrame(tick);
        };

        raf = window.requestAnimationFrame(tick);
      } catch (error) {
        if (cancelled) return;

        setCamera('denied');

        const name =
          error instanceof DOMException ? error.name : '';

        if (
          name === 'NotAllowedError' ||
          name === 'SecurityError'
        ) {
          setCameraMessage(
            'Camera permission is blocked. Allow camera access in browser settings, then retry.',
          );
        } else if (
          name === 'NotFoundError' ||
          name === 'OverconstrainedError'
        ) {
          setCameraMessage(
            'No compatible camera was found on this device.',
          );
        } else if (
          name === 'NotReadableError' ||
          name === 'AbortError'
        ) {
          setCameraMessage(
            'The camera may be busy. Close other camera apps and retry.',
          );
        } else {
          setCameraMessage(
            'Could not open the camera. Check permissions and HTTPS, then retry.',
          );
        }

        stop();
      }
    }

    void startCamera();

    return () => {
      cancelled = true;
      window.cancelAnimationFrame(raf);
      stop();
    };
  }, [cameraAttempt, stop, scan.mutate]);

  return (
    <>
      <PageHeader title="Check-in" actions={<span className="scanner-status"><span /> Scanner ready</span>} />
      <div className="scanner-hero"><div><span className="eyebrow">FRONT DESK SCANNER</span><h2>Fast, frictionless member entry.</h2><p className="muted">Scan a member QR pass or find them manually. The result appears instantly.</p></div><div className="scanner-hero-icon"><ScanLine size={26} /></div></div>

      {result && (
        <ResultCard r={result} onDismiss={dismissResult} />
      )}

      {/* Keep video mounted while the result card is displayed. */}
      <div className="scanner-shell"><div className="scanner">
        <video
          ref={videoRef}
          muted
          playsInline
          autoPlay
          aria-label="Camera preview"
          className={camera === 'on' ? '' : 'hidden'}
        />

        <canvas ref={canvasRef} className="hidden" />

        {camera === 'starting' && (
          <Spinner label="Starting camera…" />
        )}

        {camera === 'on' && (
          <p className="muted center">
            Point the camera at a member QR code.
            Scanning resumes automatically.
          </p>
        )}

        {(camera === 'denied' || camera === 'unsupported') && (
          <div className="state error" role="alert">
            <p>{cameraMessage}</p>

            <button
              className="btn"
              onClick={() => setCameraAttempt((n) => n + 1)}
            >
              Retry camera
            </button>
          </div>
        )}

        {scan.isError && <FormError error={scan.error} />}
      </div></div>

      <div className="manual-checkin-head"><div><span className="eyebrow">FALLBACK</span><h2>Manual check-in</h2><p className="muted">Use this when a member does not have their QR pass.</p></div><Keyboard size={20}/></div>

      <ManualCheckin onResult={setResult} />
    </>
  );
}

function ManualCheckin({
  onResult,
}: {
  onResult: (r: ScanResponse) => void;
}) {
  const [q, setQ] = useState('');
  const dq = useDebounced(q.trim());

  const res = useQuery({
    queryKey: ['manual-search', dq],
    queryFn: () =>
      get<Page<MemberSummary>>(
        '/api/v1/members' +
          qs({ q: dq, status: 'ACTIVE', size: 8 }),
      ),
    enabled: dq.length >= 2,
  });

  const check = useMutation({
    mutationFn: (memberId: number) =>
      post<ScanResponse>('/api/v1/attendance/manual', {
        memberId,
      }),

    onSuccess: (response) => {
      onResult(response);
      setQ('');
    },
  });

  return (
    <div className="manual-checkin-card">
      <div className="manual-search"><Keyboard size={17}/><input
        aria-label="Find member"
        placeholder="Search name, phone or member code"
        value={q}
        onChange={(event) => setQ(event.target.value)}
      /></div>

      <FormError error={check.error} />

      {dq.length < 2 ? (
        <p className="muted">Type at least 2 characters.</p>
      ) : res.isLoading ? (
        <Spinner />
      ) : res.isError ? (
        <ErrorBox error={res.error} />
      ) : res.data && res.data.items.length === 0 ? (
        <Empty title="No active member found" />
      ) : (
        <ul className="list">
          {res.data?.items.map((member) => (
            <li key={member.id} className="row">
              <div>
                <strong>{member.fullName}</strong>
                <div className="muted">
                  {member.memberCode} · {member.phone}
                </div>
              </div>

              <button
                className="btn primary"
                disabled={check.isPending}
                onClick={() => check.mutate(member.id)}
              >
                {check.isPending ? 'Checking in…' : 'Check in'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
