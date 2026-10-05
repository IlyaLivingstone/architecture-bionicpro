import React, { useCallback, useEffect, useState } from 'react';
import { fetchSession, logout, startLogin, UserSession } from '../auth/session';

const API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8000';

interface ReportAvailability {
  login: string;
  availablePeriod: string | null;
  summary: {
    days: number;
    movements: number;
    errors: number;
    avgResponseMs: number;
  };
}

const ReportPage: React.FC = () => {
  const [session, setSession] = useState<UserSession | null>(null);
  const [availability, setAvailability] = useState<ReportAvailability | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchSession()
      .then(setSession)
      .catch((err: Error) => setError(err.message))
      .finally(() => setInitialized(true));
  }, []);

  useEffect(() => {
    if (!session) {
      return;
    }
    fetch(`${API_URL}/reports/availability`, { credentials: 'include' })
      .then(async (response) => {
        if (response.status === 401) {
          setSession(null);
          return null;
        }
        return response.ok ? response.json() : null;
      })
      .then((data: ReportAvailability | null) => setAvailability(data))
      .catch(() => setAvailability(null));
  }, [session]);

  const downloadReport = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setMessage(null);

      const response = await fetch(`${API_URL}/reports`, { credentials: 'include' });
      if (response.status === 401) {
        setSession(null);
        throw new Error('Session expired, please sign in again');
      }
      if (!response.ok) {
        const details = await response.json().catch(() => null);
        throw new Error(details?.hint || details?.error || `Report service responded with ${response.status}`);
      }

      const report: { url: string; cached: boolean; period: string } = await response.json();
      setMessage(report.cached
        ? `Report is ready (period ${report.period}), downloading from CDN`
        : `Report generated and stored in S3 (period ${report.period}), downloading via CDN`);
      window.location.assign(report.url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  }, [session]);

  const signOut = useCallback(async () => {
    await logout();
    setSession(null);
    setAvailability(null);
  }, []);

  if (!initialized) {
    return <div>Loading...</div>;
  }

  if (!session) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <button
          onClick={() => startLogin(window.location.href)}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Login
        </button>
        {error && (
          <div className="mt-4 p-4 bg-red-100 text-red-700 rounded">{error}</div>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
      <div className="p-8 bg-white rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-2">Usage Reports</h1>
        <p className="mb-4 text-gray-600">
          {session.username} ({session.email})
        </p>

        {availability && (
          <p className="mb-4 text-sm text-gray-600">
            Data prepared through {availability.availablePeriod || '-'}:{' '}
            {availability.summary.days} days, {availability.summary.movements} movements,{' '}
            average response {availability.summary.avgResponseMs} ms
          </p>
        )}

        <button
          onClick={downloadReport}
          disabled={loading}
          className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${
            loading ? 'opacity-50 cursor-not-allowed' : ''
          }`}
        >
          {loading ? 'Generating Report...' : 'Download Report'}
        </button>

        <button
          onClick={signOut}
          className="ml-2 px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300"
        >
          Log out
        </button>

        {message && (
          <div className="mt-4 p-4 bg-green-100 text-green-800 rounded">{message}</div>
        )}
        {error && (
          <div className="mt-4 p-4 bg-red-100 text-red-700 rounded">{error}</div>
        )}
      </div>
    </div>
  );
};

export default ReportPage;
