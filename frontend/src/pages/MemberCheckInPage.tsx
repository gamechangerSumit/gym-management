import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import jsQR from 'jsqr';
import { Camera, CheckCircle2, RefreshCw, ShieldCheck, XCircle } from 'lucide-react';
import { post } from '../api/client';
import type { ScanResponse } from '../api/types';
import { formatDateTime } from '../lib/format';
import { PageHeader } from '../components/ui';

const PREFIX = 'GYMCHECKIN:';

export function MemberCheckInPage() {
  const qc = useQueryClient();
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const lockedRef = useRef(false);
  const [active, setActive] = useState(true);
  const [cameraAttempt, setCameraAttempt] = useState(0);
  const [cameraMessage, setCameraMessage] = useState('Starting your camera…');
  const [cameraError, setCameraError] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ScanResponse | null>(null);
  const [requestError, setRequestError] = useState('');

  useEffect(() => {
    let cancelled = false;
    let raf = 0;

    const stop = () => {
      streamRef.current?.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    };

    async function start() {
      setCameraError('');
      setCameraMessage('Starting your camera…');
      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraError('Camera access needs HTTPS (or localhost) and a supported browser.');
        return;
      }
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
        });
        if (cancelled) { stream.getTracks().forEach(track => track.stop()); return; }
        streamRef.current = stream;
        const video = videoRef.current;
        if (!video) throw new Error('Camera preview is unavailable.');
        video.srcObject = stream;
        video.muted = true;
        video.playsInline = true;
        await video.play();
        if (cancelled) { stop(); return; }
        setCameraMessage('Point your camera at the QR displayed by your gym.');
        const tick = () => {
          if (cancelled) return;
          const v = videoRef.current;
          const canvas = canvasRef.current;
          if (v && canvas && v.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && v.videoWidth > 0 && v.videoHeight > 0) {
            canvas.width = v.videoWidth;
            canvas.height = v.videoHeight;
            const ctx = canvas.getContext('2d', { willReadFrequently: true });
            if (ctx) {
              ctx.drawImage(v, 0, 0, canvas.width, canvas.height);
              const frame = ctx.getImageData(0, 0, canvas.width, canvas.height);
              const code = jsQR(frame.data, frame.width, frame.height, { inversionAttempts: 'dontInvert' });
              if (code?.data.startsWith(PREFIX) && !lockedRef.current) {
                lockedRef.current = true;
                setBusy(true);
                setActive(false);
                void post<ScanResponse>('/api/v1/me/check-in', { token: code.data })
                  .then(response => {
                    setResult(response);
                    void qc.invalidateQueries({ queryKey: ['me-att'] });
                    void qc.invalidateQueries({ queryKey: ['me-ms'] });
                    void qc.invalidateQueries({ queryKey: ['me'] });
                  })
                  .catch(error => setRequestError(error instanceof Error ? error.message : 'Could not check in. Please scan the latest gym QR.'))
                  .finally(() => setBusy(false));
                return;
              }
            }
          }
          raf = window.requestAnimationFrame(tick);
        };
        raf = window.requestAnimationFrame(tick);
      } catch (error) {
        if (cancelled) return;
        const name = error instanceof DOMException ? error.name : '';
        setCameraError(name === 'NotAllowedError' || name === 'SecurityError'
          ? 'Camera permission is blocked. Allow camera access in your browser settings, then retry.'
          : error instanceof Error ? error.message : 'Unable to start the camera.');
      }
    }

    if (active && !result && !requestError) void start();
    return () => {
      cancelled = true;
      window.cancelAnimationFrame(raf);
      stop();
    };
  }, [active, result, requestError, qc, cameraAttempt]);

  const restart = () => {
    lockedRef.current = false;
    setResult(null);
    setRequestError('');
    setCameraError('');
    setActive(true);
    setCameraAttempt(v => v + 1);
  };

  return <>
    <PageHeader title="Check in at your gym" actions={<span className="badge info"><ShieldCheck size={14}/> Member check-in</span>} />
    <section className="attendance-hero">
      <div><span className="eyebrow">SCAN GYM QR</span><h2>Scan the code displayed at reception.</h2><p className="muted">Allow camera access and keep the gym QR inside the frame. Your visit and attendance streak update after a successful check-in.</p></div>
      <div className="attendance-icon"><Camera size={25}/></div>
    </section>
    <section className="member-checkin-card panel">
      {result ? <div className="member-checkin-result">
        {result.result === 'CHECKED_IN' ? <CheckCircle2 size={48}/> : <XCircle size={48}/>}
        <h2>{result.result === 'CHECKED_IN' ? 'Attendance marked!' : result.result === 'DUPLICATE_RECENT' ? 'Already checked in' : 'Check-in not completed'}</h2>
        <p>{result.message}</p>
        {result.checkedInAt && <p className="muted">{formatDateTime(result.checkedInAt)}</p>}
        <button className="btn primary" onClick={restart}><RefreshCw size={15}/> Scan again</button>
      </div> : requestError ? <div className="member-checkin-result error">
        <XCircle size={42}/><h2>Could not check in</h2><p>{requestError}</p>
        <button className="btn primary" onClick={restart}><RefreshCw size={15}/> Try again</button>
      </div> : <>
        <div className="member-camera-frame">
          <video ref={videoRef} className="member-camera-video" aria-label="Camera preview" autoPlay muted playsInline />
          <div className="member-camera-reticle" aria-hidden="true"/>
        </div>
        <p className="muted">{busy ? 'Checking your membership…' : cameraError || cameraMessage}</p>
        {cameraError && <button className="btn primary" onClick={restart}><RefreshCw size={15}/> Retry camera</button>}
      </>}
      <canvas ref={canvasRef} className="scanner-canvas-hidden" aria-hidden="true"/>
    </section>
  </>;
}
