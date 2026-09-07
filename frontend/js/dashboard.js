(function () {
  const API_BASE = window.MINDCARE_API_BASE;

  const token = localStorage.getItem("mindcare_token");
  if (!token) {
    window.location.href = "auth.html";
    return;
  }

  function authHeaders() {
    return { Authorization: "Bearer " + token };
  }

  function clearSessionAndRedirect() {
    localStorage.removeItem("mindcare_token");
    localStorage.removeItem("mindcare_user");
    window.location.href = "auth.html";
  }

  async function api(path, options) {
    const res = await fetch(API_BASE + path, {
      ...options,
      headers: { ...(options && options.headers), ...authHeaders() },
    });
    let body = null;
    try {
      body = await res.json();
    } catch (_) {
      /* no body */
    }
    if (!res.ok) {
      throw new Error((body && body.error) || "Something went wrong. Please try again.");
    }
    return body;
  }

  const ROLE_COPY = {
    CLIENT: "Book a session with a therapist below, or check the status of one you've already requested.",
    THERAPIST: "Publish times you're free, then track the sessions booked against them.",
    CLINICAL_SUPERVISOR: "Case review and clinical oversight tools are coming soon for this role.",
    RECEPTIONIST: "Incoming requests need your approval, a reschedule, or a decline.",
    FINANCE: "Billing, invoicing, and payment tools are coming soon for this role.",
    MAINTENANCE: "You have clinic-wide visibility: every user, every appointment, and the ability to provision new staff accounts below.",
  };

  const ROLE_LABEL = {
    CLIENT: "Client",
    THERAPIST: "Therapist",
    CLINICAL_SUPERVISOR: "Clinical Supervisor",
    RECEPTIONIST: "Receptionist",
    FINANCE: "Finance",
    MAINTENANCE: "Maintenance",
  };

  const STATUS_CLASS = {
    APPROVED: "status-badge--approved",
    PENDING: "status-badge--pending",
    RESCHEDULED: "status-badge--pending",
    DECLINED: "status-badge--declined",
    CANCELLED: "status-badge--cancelled",
    COMPLETED: "status-badge--declined",
  };

  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str == null ? "" : str;
    return div.innerHTML;
  }

  function firstName(fullName) {
    const first = (fullName || "").split(" ")[0] || "there";
    return first.replace(/\.+$/, "");
  }

  function formatWhen(iso) {
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      weekday: "short", month: "short", day: "numeric",
      hour: "numeric", minute: "2-digit",
    });
  }

  function statusBadge(status) {
    return `<span class="status-badge ${STATUS_CLASS[status] || ""}">${status.toLowerCase()}</span>`;
  }

  // ---------- shared appointment card renderer ----------

  function appointmentCard(appt, { personLabel, personName, actions, extra }) {
    const joinButton = appt.status === "APPROVED" && appt.meetLink
      ? `<a class="mini-button mini-button--accent" href="${escapeHtml(appt.meetLink)}" target="_blank" rel="noopener">Join Session</a>`
      : "";
    return `
      <div class="appointment-card" data-id="${appt.id}">
        <div class="row">
          <span class="who">${personLabel}: ${escapeHtml(personName)}</span>
          ${statusBadge(appt.status)}
        </div>
        <div class="when">${formatWhen(appt.scheduledAt)} · ${appt.durationMinutes} min</div>
        ${appt.notes ? `<div class="when">${escapeHtml(appt.notes)}</div>` : ""}
        <div class="actions">${joinButton}${actions || ""}</div>
        ${extra || ""}
      </div>`;
  }

  function renderList(container, items, emptyText, itemHtmlFn) {
    if (!items.length) {
      container.innerHTML = `<p class="empty-note">${emptyText}</p>`;
      return;
    }
    container.innerHTML = items.map(itemHtmlFn).join("");
  }

  // ---------- boot ----------

  async function loadCurrentUser() {
    try {
      return await api("/api/auth/me");
    } catch (_) {
      clearSessionAndRedirect();
      return null;
    }
  }

  function renderUser(user) {
    document.getElementById("greeting").innerHTML =
      "HELLO,<br>" + escapeHtml(firstName(user.fullName)).toUpperCase() + ".";
    document.getElementById("role-pill").textContent = ROLE_LABEL[user.role] || user.role;
    document.getElementById("panel-text").textContent = ROLE_COPY[user.role] || "";

    if (user.role === "CLIENT") {
      document.getElementById("client-intake-panel").hidden = false;
      document.getElementById("client-assessments-panel").hidden = false;
      document.getElementById("client-panel").hidden = false;
      initIntakePanel();
      initClientAssessments();
      initClientPanel();
    } else if (user.role === "THERAPIST") {
      document.getElementById("therapist-panel").hidden = false;
      document.getElementById("clinical-notes-panel").hidden = false;
      initTherapistPanel();
      initClinicalNotes();
    } else if (user.role === "RECEPTIONIST") {
      document.getElementById("receptionist-panel").hidden = false;
      initReceptionistPanel();
    } else if (user.role === "MAINTENANCE") {
      document.getElementById("maintenance-panel").hidden = false;
      initMaintenancePanel();
    }
  }

  document.getElementById("logout-btn").addEventListener("click", clearSessionAndRedirect);

  // ================= CLIENT INTAKE =================

  function initIntakePanel() {
    const form = document.getElementById("intake-form");
    const errorEl = form.querySelector("[data-error]");
    const statusEl = document.getElementById("intake-status");

    api("/api/intake/mine")
      .then((intake) => {
        form.dateOfBirth.value = intake.dateOfBirth || "";
        form.phone.value = intake.phone || "";
        form.emergencyContactName.value = intake.emergencyContactName || "";
        form.emergencyContactPhone.value = intake.emergencyContactPhone || "";
        form.reasonForSeekingCare.value = intake.reasonForSeekingCare || "";
        form.consentGiven.checked = intake.consentGiven;
        statusEl.textContent = "Saved " + formatWhen(intake.updatedAt) + ".";
      })
      .catch(() => {
        statusEl.textContent = "Not submitted yet.";
      });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const data = new FormData(form);
      const button = form.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        const intake = await api("/api/intake", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            dateOfBirth: data.get("dateOfBirth") || null,
            phone: data.get("phone") || null,
            emergencyContactName: data.get("emergencyContactName") || null,
            emergencyContactPhone: data.get("emergencyContactPhone") || null,
            reasonForSeekingCare: data.get("reasonForSeekingCare") || null,
            consentGiven: form.consentGiven.checked,
          }),
        });
        statusEl.textContent = "Saved " + formatWhen(intake.updatedAt) + ".";
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        button.disabled = false;
      }
    });
  }

  // ================= CLIENT ASSESSMENTS =================

  function initClientAssessments() {
    const list = document.getElementById("client-assessment-list");
    list.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-action='take']");
      if (btn) openAssessmentForm(btn.closest("[data-id]").dataset.id);
    });
    loadClientAssessments();
  }

  async function loadClientAssessments() {
    const list = document.getElementById("client-assessment-list");
    try {
      const items = await api("/api/assessment-responses/mine");
      renderList(list, items, "Nothing assigned yet.", (item) => `
        <div class="appointment-card" data-id="${item.id}">
          <div class="row">
            <span class="who">${escapeHtml(item.templateName)}</span>
            <span class="status-badge ${item.completedAt ? "status-badge--approved" : "status-badge--pending"}">
              ${item.completedAt ? "completed" : "pending"}
            </span>
          </div>
          <div class="when">Assigned by ${escapeHtml(item.assignedByName)} · ${formatWhen(item.assignedAt)}</div>
          ${item.completedAt
            ? `<div class="when">Score ${item.totalScore} — ${escapeHtml(item.interpretation)}</div>`
            : `<div class="actions"><button class="mini-button mini-button--accent" data-action="take">Take Assessment</button></div>`}
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load assessments.</p>';
    }
  }

  async function openAssessmentForm(responseId) {
    const wrap = document.getElementById("assessment-take-wrap");
    wrap.innerHTML = '<p class="empty-note">Loading…</p>';
    let detail;
    try {
      detail = await api(`/api/assessment-responses/${responseId}`);
    } catch (_) {
      wrap.innerHTML = '<p class="empty-note">Could not load this assessment.</p>';
      return;
    }

    wrap.innerHTML = `
      <h2>${escapeHtml(detail.templateName)}</h2>
      <p class="hero-sub" style="margin-bottom:14px">${escapeHtml(detail.templateDescription || "")}</p>
      <form class="auth-form" id="take-assessment-form" novalidate>
        ${detail.questions.map((q, i) => `
          <label class="field">
            <span>${i + 1}. ${escapeHtml(q.questionText)}</span>
            <select name="q_${q.id}" required>
              <option value="">Choose…</option>
              ${Array.from({ length: q.maxScore - q.minScore + 1 }, (_, n) => q.minScore + n)
                .map((v) => `<option value="${v}">${v}</option>`).join("")}
            </select>
          </label>`).join("")}
        <p class="form-error" data-error hidden></p>
        <button type="submit" class="pill-button">
          <span>Submit</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </button>
      </form>`;

    const form = document.getElementById("take-assessment-form");
    const errorEl = form.querySelector("[data-error]");
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const data = new FormData(form);
      const answers = detail.questions.map((q) => ({
        questionId: q.id,
        score: Number(data.get(`q_${q.id}`)),
      }));
      const button = form.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        await api(`/api/assessment-responses/${responseId}/submit`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ answers }),
        });
        wrap.innerHTML = "";
        loadClientAssessments();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
        button.disabled = false;
      }
    });
  }

  // ================= CLIENT =================

  let selectedSlotId = null;

  function initClientPanel() {
    const select = document.getElementById("therapist-select");
    const slotList = document.getElementById("client-slot-list");
    const bookForm = document.getElementById("book-form");
    const bookSubmit = document.getElementById("book-submit");
    const errorEl = bookForm.querySelector("[data-error]");

    api("/api/therapists")
      .then((therapists) => {
        select.innerHTML = '<option value="">Choose a therapist…</option>' +
          therapists.map((t) => `<option value="${t.id}">${escapeHtml(t.fullName)}</option>`).join("");
        if (!therapists.length) {
          slotList.innerHTML = '<p class="empty-note">No therapists are available to book with yet.</p>';
        }
      })
      .catch(() => { slotList.innerHTML = '<p class="empty-note">Could not load therapists.</p>'; });

    select.addEventListener("change", async () => {
      selectedSlotId = null;
      bookSubmit.disabled = true;
      if (!select.value) {
        slotList.innerHTML = '<p class="empty-note">Choose a therapist to see open times.</p>';
        return;
      }
      slotList.innerHTML = '<p class="empty-note">Loading…</p>';
      try {
        const slots = await api(`/api/availability/therapist/${select.value}`);
        renderList(slotList, slots, "No open times right now — check back later.", (slot) => `
          <div class="slot-option" data-slot-id="${slot.id}">
            <span>${formatWhen(slot.startTime)}</span>
          </div>`);
      } catch (_) {
        slotList.innerHTML = '<p class="empty-note">Could not load open times.</p>';
      }
    });

    slotList.addEventListener("click", (e) => {
      const option = e.target.closest(".slot-option");
      if (!option) return;
      slotList.querySelectorAll(".slot-option").forEach((el) => el.classList.remove("selected"));
      option.classList.add("selected");
      selectedSlotId = option.dataset.slotId;
      bookSubmit.disabled = false;
    });

    bookForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      if (!selectedSlotId) return;
      bookSubmit.disabled = true;
      try {
        await api("/api/appointments", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            availabilitySlotId: selectedSlotId,
            notes: new FormData(bookForm).get("notes") || null,
          }),
        });
        bookForm.reset();
        selectedSlotId = null;
        slotList.innerHTML = '<p class="empty-note">Choose a therapist to see open times.</p>';
        select.value = "";
        loadClientAppointments();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        bookSubmit.disabled = true;
      }
    });

    document.getElementById("client-appointments").addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-action='cancel']");
      if (!btn) return;
      btn.disabled = true;
      try {
        await api(`/api/appointments/${btn.closest(".appointment-card").dataset.id}/cancel`, { method: "PATCH" });
        loadClientAppointments();
      } catch (_) {
        btn.disabled = false;
      }
    });

    loadClientAppointments();
  }

  async function loadClientAppointments() {
    const container = document.getElementById("client-appointments");
    try {
      const appts = await api("/api/appointments/mine");
      renderList(container, appts, "No sessions booked yet.", (appt) => appointmentCard(appt, {
        personLabel: "With",
        personName: appt.therapistName,
        actions: ["PENDING", "APPROVED", "RESCHEDULED"].includes(appt.status)
          ? '<button class="mini-button mini-button--danger" data-action="cancel">Cancel</button>' : "",
      }));
    } catch (_) {
      container.innerHTML = '<p class="empty-note">Could not load your sessions.</p>';
    }
  }

  // ================= THERAPIST =================

  function toIso(datetimeLocalValue) {
    return new Date(datetimeLocalValue).toISOString();
  }

  function initTherapistPanel() {
    const form = document.getElementById("availability-form");
    const errorEl = form.querySelector("[data-error]");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const data = new FormData(form);
      const button = form.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        await api("/api/availability", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            startTime: toIso(data.get("startTime")),
            endTime: toIso(data.get("endTime")),
          }),
        });
        form.reset();
        loadTherapistSlots();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        button.disabled = false;
      }
    });

    document.getElementById("therapist-slots").addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-action='remove-slot']");
      if (!btn) return;
      btn.disabled = true;
      try {
        await api(`/api/availability/${btn.closest("[data-id]").dataset.id}`, { method: "DELETE" });
        loadTherapistSlots();
      } catch (_) {
        btn.disabled = false;
      }
    });

    document.getElementById("therapist-appointments").addEventListener("click", async (e) => {
      const card = e.target.closest(".appointment-card");
      if (!card) return;
      const id = card.dataset.id;

      if (e.target.closest("[data-action='complete']")) {
        e.target.disabled = true;
        try {
          await api(`/api/appointments/${id}/complete`, { method: "PATCH" });
          loadTherapistAppointments();
        } catch (_) {
          e.target.disabled = false;
        }
      } else if (e.target.closest("[data-action='save-meet-link']")) {
        const input = card.querySelector("input[data-meet-link-input]");
        const errorEl = card.querySelector("[data-meet-error]");
        errorEl.hidden = true;
        if (!input.value.trim()) return;
        e.target.disabled = true;
        try {
          await api(`/api/appointments/${id}/meet-link`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ meetLink: input.value.trim() }),
          });
          loadTherapistAppointments();
        } catch (err) {
          errorEl.textContent = err.message;
          errorEl.hidden = false;
          e.target.disabled = false;
        }
      }
    });

    loadTherapistSlots();
    loadTherapistAppointments();
    initAssignAssessmentForm();
  }

  function initAssignAssessmentForm() {
    const form = document.getElementById("assign-assessment-form");
    const templateSelect = document.getElementById("assign-template-select");
    const errorEl = form.querySelector("[data-error]");

    api("/api/assessment-templates")
      .then((templates) => {
        templateSelect.innerHTML = '<option value="">Choose an assessment…</option>' +
          templates.map((t) => `<option value="${t.id}">${escapeHtml(t.name)}</option>`).join("");
      })
      .catch(() => {});

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const data = new FormData(form);
      const button = form.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        await api("/api/assessment-responses", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            clientId: data.get("clientId"),
            templateId: data.get("templateId"),
          }),
        });
        form.reset();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        button.disabled = false;
      }
    });
  }

  function populateClientOptions(appointments) {
    const seen = new Map();
    appointments.forEach((a) => seen.set(a.clientId, a.clientName));
    const optionsHtml = '<option value="">Choose a client…</option>' +
      [...seen.entries()].map(([id, name]) => `<option value="${id}">${escapeHtml(name)}</option>`).join("");
    ["assign-client-select", "notes-client-select"].forEach((elId) => {
      const el = document.getElementById(elId);
      if (el) el.innerHTML = optionsHtml;
    });
  }

  async function loadTherapistSlots() {
    const container = document.getElementById("therapist-slots");
    try {
      const slots = await api("/api/availability/mine");
      renderList(container, slots, "You haven't published any times yet.", (slot) => `
        <div class="appointment-card" data-id="${slot.id}">
          <div class="row">
            <span class="who">${formatWhen(slot.startTime)}</span>
            <span class="status-badge ${slot.booked ? "status-badge--approved" : "status-badge--pending"}">${slot.booked ? "booked" : "open"}</span>
          </div>
          ${!slot.booked ? '<div class="actions"><button class="mini-button mini-button--danger" data-action="remove-slot">Remove</button></div>' : ""}
        </div>`);
    } catch (_) {
      container.innerHTML = '<p class="empty-note">Could not load your slots.</p>';
    }
  }

  async function loadTherapistAppointments() {
    const container = document.getElementById("therapist-appointments");
    try {
      const appts = await api("/api/appointments/mine");
      populateClientOptions(appts);
      renderList(container, appts, "No sessions booked with you yet.", (appt) => appointmentCard(appt, {
        personLabel: "Client",
        personName: appt.clientName,
        actions: appt.status === "APPROVED"
          ? '<button class="mini-button" data-action="complete">Mark Complete</button>' : "",
        extra: appt.status === "APPROVED" && !appt.meetLink ? `
          <div class="row" style="margin-top:6px">
            <a class="mini-button" href="https://meet.google.com/new" target="_blank" rel="noopener">Start Google Meet</a>
          </div>
          <div class="row" style="margin-top:6px">
            <input type="text" placeholder="Paste the meet.google.com link here" style="flex:1" data-meet-link-input>
            <button class="mini-button mini-button--accent" data-action="save-meet-link">Save Link</button>
          </div>
          <p class="form-error" data-meet-error hidden></p>
        ` : "",
      }));
    } catch (_) {
      container.innerHTML = '<p class="empty-note">Could not load your sessions.</p>';
    }
  }

  // ================= CLINICAL NOTES =================

  const NOTE_FIELDS = [
    ["sessionType", "Session type"],
    ["presentingConcerns", "Presenting concerns"],
    ["clinicalObservations", "Clinical observations"],
    ["interventions", "Interventions"],
    ["clientResponse", "Client response"],
    ["riskAssessment", "Risk / safety assessment"],
    ["plan", "Plan"],
    ["followUp", "Follow-up"],
  ];

  let notesSelectedClientId = null;

  function initClinicalNotes() {
    const select = document.getElementById("notes-client-select");
    const newNoteBtn = document.getElementById("new-note-btn");

    select.addEventListener("change", () => {
      notesSelectedClientId = select.value || null;
      newNoteBtn.disabled = !notesSelectedClientId;
      document.getElementById("note-form-wrap").innerHTML = "";
      if (notesSelectedClientId) {
        loadNotesForClient(notesSelectedClientId);
      } else {
        document.getElementById("clinical-notes-list").innerHTML = '<p class="empty-note">Choose a client to see their notes.</p>';
      }
    });

    newNoteBtn.addEventListener("click", () => {
      if (notesSelectedClientId) openNoteForm(null, notesSelectedClientId);
    });

    document.getElementById("clinical-notes-list").addEventListener("click", async (e) => {
      const card = e.target.closest("[data-id]");
      if (!card) return;
      const id = card.dataset.id;

      if (e.target.closest("[data-action='edit']")) {
        const detail = await api(`/api/clinical-notes/${id}`);
        openNoteForm(detail, notesSelectedClientId);
      } else if (e.target.closest("[data-action='sign']")) {
        e.target.disabled = true;
        try {
          await api(`/api/clinical-notes/${id}/sign`, { method: "PATCH" });
          loadNotesForClient(notesSelectedClientId);
        } catch (err) {
          alert(err.message);
          e.target.disabled = false;
        }
      } else if (e.target.closest("[data-action='amend']")) {
        e.target.disabled = true;
        try {
          await api(`/api/clinical-notes/${id}/amend`, { method: "POST" });
          loadNotesForClient(notesSelectedClientId);
        } catch (err) {
          alert(err.message);
          e.target.disabled = false;
        }
      }
    });
  }

  async function loadNotesForClient(clientId) {
    const list = document.getElementById("clinical-notes-list");
    list.innerHTML = '<p class="empty-note">Loading…</p>';
    try {
      const notes = await api(`/api/clinical-notes/client/${clientId}`);
      renderList(list, notes, "No notes yet for this client.", (n) => `
        <div class="appointment-card" data-id="${n.id}">
          <div class="row">
            <span class="who">${escapeHtml(n.sessionType || "Untitled session")}</span>
            <span class="status-badge ${n.status === "SIGNED" ? "status-badge--approved" : "status-badge--pending"}">${n.status.toLowerCase()}</span>
          </div>
          <div class="when">${formatWhen(n.createdAt)}${n.amendsNoteId ? " · amendment" : ""}</div>
          <div class="actions">
            ${n.status === "DRAFT"
              ? '<button class="mini-button" data-action="edit">Edit</button><button class="mini-button mini-button--accent" data-action="sign">Sign</button>'
              : '<button class="mini-button" data-action="amend">Amend</button>'}
          </div>
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load notes.</p>';
    }
  }

  function openNoteForm(note, clientId) {
    const wrap = document.getElementById("note-form-wrap");
    wrap.innerHTML = `
      <h2>${note ? "Edit draft" : "New note"}</h2>
      <form class="auth-form" id="note-form" novalidate>
        ${NOTE_FIELDS.map(([key, label]) => `
          <label class="field">
            <span>${label}</span>
            <input type="text" name="${key}" value="${note ? escapeHtml(note[key] || "") : ""}">
          </label>`).join("")}
        <p class="form-error" data-error hidden></p>
        <button type="submit" class="pill-button">
          <span>Save Draft</span>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
        </button>
      </form>`;

    const form = document.getElementById("note-form");
    const errorEl = form.querySelector("[data-error]");
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const data = new FormData(form);
      const content = {};
      NOTE_FIELDS.forEach(([key]) => { content[key] = data.get(key) || null; });

      const button = form.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        if (note) {
          await api(`/api/clinical-notes/${note.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(content),
          });
        } else {
          await api("/api/clinical-notes", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ clientId, content }),
          });
        }
        wrap.innerHTML = "";
        loadNotesForClient(clientId);
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
        button.disabled = false;
      }
    });
  }

  // ================= RECEPTIONIST =================

  function initReceptionistPanel() {
    document.getElementById("receptionist-pending").addEventListener("click", async (e) => {
      const card = e.target.closest(".appointment-card");
      if (!card) return;
      const id = card.dataset.id;

      if (e.target.closest("[data-action='approve']")) {
        e.target.disabled = true;
        try {
          await api(`/api/appointments/${id}/approve`, { method: "PATCH" });
          loadReceptionistAppointments();
        } catch (err) {
          alert(err.message);
          e.target.disabled = false;
        }
      } else if (e.target.closest("[data-action='decline']")) {
        e.target.disabled = true;
        try {
          await api(`/api/appointments/${id}/decline`, { method: "PATCH" });
          loadReceptionistAppointments();
        } catch (_) {
          e.target.disabled = false;
        }
      } else if (e.target.closest("[data-action='reschedule']")) {
        const input = card.querySelector("input[type=datetime-local]");
        if (!input.value) return;
        e.target.disabled = true;
        try {
          await api(`/api/appointments/${id}/reschedule`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ newScheduledAt: toIso(input.value) }),
          });
          loadReceptionistAppointments();
        } catch (err) {
          alert(err.message);
          e.target.disabled = false;
        }
      }
    });

    loadReceptionistAppointments();
  }

  async function loadReceptionistAppointments() {
    const pendingEl = document.getElementById("receptionist-pending");
    const allEl = document.getElementById("receptionist-all");
    try {
      const appts = await api("/api/appointments");
      const pending = appts.filter((a) => a.status === "PENDING" || a.status === "RESCHEDULED");

      renderList(pendingEl, pending, "Nothing waiting on you right now.", (appt) => `
        <div class="appointment-card" data-id="${appt.id}">
          <div class="row">
            <span class="who">${escapeHtml(appt.clientName)} → ${escapeHtml(appt.therapistName)}</span>
            ${statusBadge(appt.status)}
          </div>
          <div class="when">${formatWhen(appt.scheduledAt)} · ${appt.durationMinutes} min</div>
          <div class="actions">
            <button class="mini-button mini-button--accent" data-action="approve">Approve</button>
            <button class="mini-button mini-button--danger" data-action="decline">Decline</button>
          </div>
          <div class="row">
            <input type="datetime-local" style="flex:1">
            <button class="mini-button" data-action="reschedule">Reschedule</button>
          </div>
        </div>`);

      renderList(allEl, appts, "No appointments yet.", (appt) => appointmentCard(appt, {
        personLabel: "Client",
        personName: `${appt.clientName} → ${appt.therapistName}`,
      }));
    } catch (_) {
      pendingEl.innerHTML = '<p class="empty-note">Could not load appointments.</p>';
    }
  }

  // ================= MAINTENANCE =================

  function initMaintenancePanel() {
    loadClinicOverview();
    loadMaintenanceAppointments();

    const staffForm = document.getElementById("staff-form");
    staffForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      const errorEl = staffForm.querySelector("[data-error]");
      errorEl.hidden = true;

      const data = new FormData(staffForm);
      const button = staffForm.querySelector("button[type=submit]");
      button.disabled = true;

      try {
        await api("/api/staff", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            fullName: data.get("fullName"),
            email: data.get("email"),
            password: data.get("password"),
            role: data.get("role"),
          }),
        });
        staffForm.reset();
        loadClinicOverview();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        button.disabled = false;
      }
    });
  }

  async function loadClinicOverview() {
    try {
      const users = await api("/api/staff");
      const counts = users.reduce((acc, u) => {
        acc[u.role] = (acc[u.role] || 0) + 1;
        return acc;
      }, {});

      const statRow = document.getElementById("stat-row");
      statRow.innerHTML = "";
      ["CLIENT", "THERAPIST", "CLINICAL_SUPERVISOR", "RECEPTIONIST", "FINANCE", "MAINTENANCE"].forEach((role) => {
        const tile = document.createElement("div");
        tile.className = "stat-tile";
        tile.innerHTML =
          '<div class="num">' + (counts[role] || 0) + "</div>" +
          '<div class="lbl">' + ROLE_LABEL[role] + "s</div>";
        statRow.appendChild(tile);
      });

      const body = document.getElementById("user-table-body");
      body.innerHTML = "";
      users.forEach((u) => {
        const row = document.createElement("tr");
        row.innerHTML =
          "<td>" + escapeHtml(u.fullName) + "</td>" +
          "<td>" + escapeHtml(u.email) + "</td>" +
          "<td>" + ROLE_LABEL[u.role] + "</td>";
        body.appendChild(row);
      });
    } catch (_) {
      /* leave panel as-is on failure */
    }
  }

  async function loadMaintenanceAppointments() {
    const container = document.getElementById("maintenance-appointments");
    try {
      const appts = await api("/api/appointments");
      renderList(container, appts, "No appointments yet.", (appt) => appointmentCard(appt, {
        personLabel: "Client",
        personName: `${appt.clientName} → ${appt.therapistName}`,
      }));
    } catch (_) {
      container.innerHTML = '<p class="empty-note">Could not load appointments.</p>';
    }
  }

  loadCurrentUser().then((user) => {
    if (user) renderUser(user);
  });
})();
