import { useQuery } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import { Clock3, RefreshCw, ShieldCheck } from 'lucide-react';
import { post } from '../api/client';
import { ErrorBox, PageHeader, Spinner } from '../components/ui';

interface GymQr {
  payload: string;
  expiresAt: string;
}

export function GymQrPage() {
  const qr = useQuery({
    queryKey: ['gym-checkin-qr'],
    queryFn: () => post<GymQr>('/api/v1/attendance/gym-qr', {}),
    refetchInterval: 20_000,
    refetchOnWindowFocus: true,
    retry: 1,
  });

  return <>
    <PageHeader title="Member self check-in" actions={<span className="badge good"><ShieldCheck size={14}/> Rotating secure QR</span>} />
    <section className="attendance-hero">
      <div><span className="eyebrow">DISPLAY AT RECEPTION</span><h2>Members scan this code to check in.</h2><p className="muted">This QR refreshes automatically every 20 seconds and expires after 30 seconds. Keep this screen visible at the gym entrance.</p></div>
      <div className="attendance-icon"><RefreshCw size={25}/></div>
    </section>
    <section className="gym-qr-display panel">
      {qr.isLoading && <Spinner label="Generating secure gym QR…"/>}
      {qr.isError && <ErrorBox error={qr.error} onRetry={() => void qr.refetch()}/>}
      {qr.data && <div className="gym-qr-content">
        <div className="qr-stage"><QRCodeSVG value={qr.data.payload} size={300} marginSize={3} level="M" aria-label="Rotating gym check-in QR code"/></div>
        <h2>Scan to mark your visit</h2>
        <p className="muted">Open the Gym app on your phone, choose Check-in, and point your camera at this code.</p>
        <p className="gym-qr-expiry"><Clock3 size={15}/> Expires at {new Date(qr.data.expiresAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'})}</p>
        <button className="btn" onClick={() => void qr.refetch()} disabled={qr.isFetching}><RefreshCw size={15}/> {qr.isFetching ? 'Refreshing…' : 'Refresh QR now'}</button>
      </div>}
    </section>
  </>;
}
