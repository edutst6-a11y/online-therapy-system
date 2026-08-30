import React, { useState, useEffect } from 'react';

export default function Dashboard({ user, onLogout }) {
  const [appointments, setAppointments] = useState([]);
  const [therapists, setTherapists] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState({ therapistId: '', appointmentDate: '' });

  useEffect(() => {
    fetchAppointments();
    if (user.role === 'CLIENT') {
      fetchTherapists();
    }
  }, [user]);

  const fetchAppointments = async () => {
    const endpoint = user.role === 'CLIENT' 
      ? `/api/appointments/client/${user.id}`
      : `/api/appointments/therapist/${user.id}`;
    const res = await fetch(`http://localhost:8080${endpoint}`);
    if (res.ok) setAppointments(await res.json());
  };

  const fetchTherapists = async () => {
    const res = await fetch('http://localhost:8080/api/users/therapists');
    if (res.ok) setTherapists(await res.json());
  };

  const handleBookSession = async (e) => {
    e.preventDefault();
    const payload = {
      clientId: user.id,
      therapistId: parseInt(formData.therapistId),
      appointmentDate: formData.appointmentDate,
      status: 'SCHEDULED'
    };

    const res = await fetch('http://localhost:8080/api/appointments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      setShowModal(false);
      setFormData({ therapistId: '', appointmentDate: '' });
      fetchAppointments();
    }
  };

  return (
    <div style={{ fontFamily: 'sans-serif', backgroundColor: '#f4f7f6', minHeight: '100vh' }}>
      <header style={{ backgroundColor: '#0e7490', color: '#fff', padding: '16px 32px' }}>
        <h1 style={{ margin: 0, fontSize: '20px' }}>MindCare Portal</h1>
      </header>

      <main style={{ maxWidth: '1000px', margin: '32px auto', padding: '0 16px' }}>
        <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' }}>
          <h2 style={{ margin: 0, color: '#0f766e' }}>Welcome, {user.name} ({user.role})</h2>
          <div>
            {user.role === 'CLIENT' && (
              <button 
                onClick={() => setShowModal(true)} 
                style={{ backgroundColor: '#0f766e', color: '#fff', border: 'none', padding: '10px 16px', borderRadius: '4px', cursor: 'pointer', marginRight: '10px' }}
              >
                + Book Session
              </button>
            )}
            <button 
              onClick={onLogout} 
              style={{ backgroundColor: '#e2e8f0', border: 'none', padding: '10px 16px', borderRadius: '4px', cursor: 'pointer' }}
            >
              Logout
            </button>
          </div>
        </div>

        {user.role === 'CLIENT' ? (
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px', marginTop: '24px' }}>
            <section style={{ background: '#fff', padding: '20px', borderRadius: '8px' }}>
              <h3>Your Scheduled Sessions</h3>
              {appointments.length === 0 ? <p style={{ color: '#64748b' }}>No upcoming sessions.</p> : (
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid #e2e8f0', textAlign: 'left' }}>
                      <th style={{ padding: '8px' }}>Date & Time</th>
                      <th style={{ padding: '8px' }}>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {appointments.map(app => (
                      <tr key={app.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '12px 8px' }}>{new Date(app.appointmentDate).toLocaleString()}</td>
                        <td style={{ padding: '12px 8px' }}>
                          <span style={{ background: app.status === 'COMPLETED' ? '#dcfce7' : '#e0f2fe', color: app.status === 'COMPLETED' ? '#15803d' : '#0369a1', padding: '4px 8px', borderRadius: '12px', fontSize: '12px' }}>
                            {app.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>

            <section style={{ background: '#fff', padding: '20px', borderRadius: '8px' }}>
              <h3>Available Clinicians</h3>
              {therapists.map(t => (
                <div key={t.id} style={{ borderBottom: '1px solid #f1f5f9', padding: '10px 0' }}>
                  <strong>{t.name}</strong>
                  <div style={{ fontSize: '13px', color: '#64748b' }}>{t.email}</div>
                </div>
              ))}
            </section>
          </div>
        ) : (
          <section style={{ background: '#fff', padding: '20px', borderRadius: '8px', marginTop: '24px' }}>
            <h3>Upcoming Client Appointments</h3>
            {appointments.length === 0 ? <p style={{ color: '#64748b' }}>No client appointments booked.</p> : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid #e2e8f0', textAlign: 'left' }}>
                    <th style={{ padding: '8px' }}>Client ID</th>
                    <th style={{ padding: '8px' }}>Date & Time</th>
                    <th style={{ padding: '8px' }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {appointments.map(app => (
                    <tr key={app.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={{ padding: '12px 8px' }}>Client #{app.clientId}</td>
                      <td style={{ padding: '12px 8px' }}>{new Date(app.appointmentDate).toLocaleString()}</td>
                      <td style={{ padding: '12px 8px' }}>
                        <span style={{ background: '#e0f2fe', color: '#0369a1', padding: '4px 8px', borderRadius: '12px', fontSize: '12px' }}>
                          {app.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        )}

        {showModal && (
          <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ background: '#fff', padding: '24px', borderRadius: '8px', width: '400px' }}>
              <h3>Book a New Therapy Session</h3>
              <form onSubmit={handleBookSession}>
                <div style={{ marginBottom: '16px' }}>
                  <label style={{ display: 'block', marginBottom: '4px' }}>Select Therapist</label>
                  <select 
                    required 
                    style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #ccc' }}
                    value={formData.therapistId} 
                    onChange={e => setFormData({ ...formData, therapistId: e.target.value })}
                  >
                    <option value="">-- Choose Clinician --</option>
                    {therapists.map(t => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>
                <div style={{ marginBottom: '16px' }}>
                  <label style={{ display: 'block', marginBottom: '4px' }}>Date & Time</label>
                  <input 
                    type="datetime-local" 
                    required 
                    style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #ccc' }}
                    value={formData.appointmentDate} 
                    onChange={e => setFormData({ ...formData, appointmentDate: e.target.value })}
                  />
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                  <button type="button" onClick={() => setShowModal(false)} style={{ padding: '8px 12px', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>Cancel</button>
                  <button type="submit" style={{ padding: '8px 12px', backgroundColor: '#0f766e', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>Confirm Booking</button>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}