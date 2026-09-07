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
    FINANCE: "Build invoices, record payments, and track the clinic's finances below.",
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
      document.getElementById("messages-panel").hidden = false;
      document.getElementById("documents-panel").hidden = false;
      document.getElementById("billing-panel").hidden = false;
      initIntakePanel();
      initClientAssessments();
      initClientPanel();
      initMessagesPanel(user.role);
      initDocumentsPanel(user.role);
      initClientBillingPanel();
    } else if (user.role === "THERAPIST") {
      document.getElementById("therapist-panel").hidden = false;
      document.getElementById("clinical-notes-panel").hidden = false;
      document.getElementById("treatment-plan-panel").hidden = false;
      document.getElementById("messages-panel").hidden = false;
      document.getElementById("documents-panel").hidden = false;
      initTherapistPanel();
      initClinicalNotes();
      initTreatmentPlans();
      initMessagesPanel(user.role);
      initDocumentsPanel(user.role);
    } else if (user.role === "RECEPTIONIST") {
      document.getElementById("receptionist-panel").hidden = false;
      initReceptionistPanel();
    } else if (user.role === "FINANCE") {
      document.getElementById("finance-panel").hidden = false;
      initFinancePanel();
    } else if (user.role === "MAINTENANCE") {
      document.getElementById("maintenance-panel").hidden = false;
      initMaintenancePanel();
    }
  }

  document.getElementById("logout-btn").addEventListener("click", clearSessionAndRedirect);

  // ================= NOTIFICATIONS =================

  const NOTIF_TYPE_LABEL = {
    APPOINTMENT_REQUESTED: "Session request",
    APPOINTMENT_APPROVED: "Session approved",
    APPOINTMENT_DECLINED: "Session declined",
    APPOINTMENT_RESCHEDULED: "Session rescheduled",
    APPOINTMENT_CANCELLED: "Session cancelled",
    APPOINTMENT_COMPLETED: "Session completed",
    NEW_MESSAGE: "New message",
    ASSESSMENT_ASSIGNED: "Assessment assigned",
  };

  function initNotifications() {
    const bell = document.getElementById("notif-bell");
    const dropdown = document.getElementById("notif-dropdown");
    const badge = document.getElementById("notif-badge");
    const list = document.getElementById("notif-list");
    const markAllBtn = document.getElementById("notif-mark-all");

    function setBadge(count) {
      if (count > 0) {
        badge.textContent = count > 99 ? "99+" : String(count);
        badge.hidden = false;
      } else {
        badge.hidden = true;
      }
    }

    async function refreshBadge() {
      try {
        const { count } = await api("/api/notifications/unread-count");
        setBadge(count);
      } catch (_) {
        /* leave badge as-is */
      }
    }

    async function loadList() {
      list.innerHTML = '<p class="empty-note">Loading…</p>';
      try {
        const notifications = await api("/api/notifications/mine");
        renderList(list, notifications, "No notifications yet.", (n) => `
          <div class="notif-item${n.readAt ? "" : " unread"}" data-id="${n.id}">
            <div class="notif-title">${escapeHtml(NOTIF_TYPE_LABEL[n.type] || n.title)}</div>
            <div class="notif-body">${escapeHtml(n.body)}</div>
            <div class="notif-time">${formatWhen(n.createdAt)}</div>
          </div>`);
      } catch (_) {
        list.innerHTML = '<p class="empty-note">Could not load notifications.</p>';
      }
    }

    function positionDropdown() {
      const bellRect = bell.getBoundingClientRect();
      const stageRect = document.querySelector(".stage").getBoundingClientRect();
      dropdown.style.top = (bellRect.bottom - stageRect.top + 10) + "px";
      dropdown.style.left = (bellRect.left - stageRect.left) + "px";
    }

    bell.addEventListener("click", (e) => {
      e.stopPropagation();
      dropdown.hidden = !dropdown.hidden;
      if (!dropdown.hidden) {
        positionDropdown();
        loadList();
      }
    });

    document.addEventListener("click", (e) => {
      if (!dropdown.hidden && !dropdown.contains(e.target) && e.target !== bell) {
        dropdown.hidden = true;
      }
    });

    list.addEventListener("click", async (e) => {
      const item = e.target.closest(".notif-item.unread");
      if (!item) return;
      try {
        await api(`/api/notifications/${item.dataset.id}/read`, { method: "PATCH" });
        item.classList.remove("unread");
        refreshBadge();
      } catch (_) {
        /* leave as unread on failure */
      }
    });

    markAllBtn.addEventListener("click", async () => {
      try {
        await api("/api/notifications/read-all", { method: "PATCH" });
        list.querySelectorAll(".notif-item.unread").forEach((el) => el.classList.remove("unread"));
        refreshBadge();
      } catch (_) {
        /* no-op */
      }
    });

    refreshBadge();
    setInterval(refreshBadge, 30000);
  }

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
    ["assign-client-select", "notes-client-select", "plan-client-select", "documents-client-select"].forEach((elId) => {
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

  // ================= TREATMENT PLANS =================

  function initTreatmentPlans() {
    const select = document.getElementById("plan-client-select");
    const form = document.getElementById("new-plan-form");
    const errorEl = form.querySelector("[data-error]");
    let selectedClientId = null;

    select.addEventListener("change", () => {
      selectedClientId = select.value || null;
      document.getElementById("new-plan-btn").disabled = true;
      if (selectedClientId) {
        loadPlanForClient(selectedClientId);
      } else {
        document.getElementById("plan-detail-wrap").innerHTML = '<p class="empty-note">Choose a client to see their plan.</p>';
      }
    });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      if (!selectedClientId) return;
      const data = new FormData(form);
      const button = document.getElementById("new-plan-btn");
      button.disabled = true;
      try {
        const plan = await api("/api/treatment-plans", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            clientId: selectedClientId,
            reviewDate: data.get("reviewDate") || null,
            goals: [],
          }),
        });
        renderPlanDetail(plan);
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
        button.disabled = false;
      }
    });
  }

  async function loadPlanForClient(clientId) {
    const wrap = document.getElementById("plan-detail-wrap");
    const newPlanBtn = document.getElementById("new-plan-btn");
    wrap.innerHTML = '<p class="empty-note">Loading…</p>';
    try {
      const plans = await api(`/api/treatment-plans/client/${clientId}`);
      if (!plans.length) {
        wrap.innerHTML = '<p class="empty-note">No treatment plan yet — start one on the left.</p>';
        newPlanBtn.disabled = false;
        return;
      }
      newPlanBtn.disabled = true;
      renderPlanDetail(plans[0]);
    } catch (_) {
      wrap.innerHTML = '<p class="empty-note">Could not load treatment plan.</p>';
    }
  }

  function goalStatusClass(status) {
    if (status === "ACHIEVED") return "status-badge--approved";
    if (status === "DISCONTINUED") return "status-badge--cancelled";
    return "status-badge--pending";
  }

  function renderPlanDetail(plan) {
    const wrap = document.getElementById("plan-detail-wrap");
    wrap.innerHTML = `
      <h2>Plan for ${escapeHtml(plan.clientName)}</h2>
      <p class="hero-sub" style="margin-bottom:10px">
        Responsible: ${escapeHtml(plan.clinicianName)}${plan.reviewDate ? " · Review " + plan.reviewDate : ""}
        <span class="status-badge ${goalStatusClass(plan.status)}" style="margin-left:8px">${plan.status.toLowerCase()}</span>
      </p>
      <div class="appointment-list" id="goal-list">
        ${plan.goals.map((g) => `
          <div class="appointment-card" data-goal-id="${g.id}">
            <div class="row">
              <span class="who">${escapeHtml(g.description)}</span>
              <span class="status-badge ${goalStatusClass(g.status)}">${g.status.toLowerCase()}</span>
            </div>
            ${g.interventions ? `<div class="when">${escapeHtml(g.interventions)}</div>` : ""}
            <div class="actions">
              ${g.status !== "ACHIEVED" ? '<button class="mini-button mini-button--accent" data-action="achieve">Mark Achieved</button>' : ""}
              ${g.status !== "DISCONTINUED" ? '<button class="mini-button mini-button--danger" data-action="discontinue">Discontinue</button>' : ""}
            </div>
          </div>`).join("")}
      </div>
      <form class="auth-form" id="add-goal-form" novalidate style="margin-top:14px">
        <label class="field"><span>New goal</span><input type="text" name="description" required></label>
        <label class="field"><span>Interventions (optional)</span><input type="text" name="interventions"></label>
        <p class="form-error" data-error hidden></p>
        <button type="submit" class="mini-button mini-button--accent">Add Goal</button>
      </form>`;

    document.getElementById("goal-list").addEventListener("click", async (e) => {
      const card = e.target.closest("[data-goal-id]");
      if (!card) return;
      const goalId = card.dataset.goalId;
      let status = null;
      if (e.target.closest("[data-action='achieve']")) status = "ACHIEVED";
      else if (e.target.closest("[data-action='discontinue']")) status = "DISCONTINUED";
      if (!status) return;
      e.target.disabled = true;
      try {
        const updated = await api(`/api/treatment-plans/goals/${goalId}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status }),
        });
        renderPlanDetail(updated);
      } catch (err) {
        alert(err.message);
        e.target.disabled = false;
      }
    });

    const addGoalForm = document.getElementById("add-goal-form");
    addGoalForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const errorEl = addGoalForm.querySelector("[data-error]");
      errorEl.hidden = true;
      const data = new FormData(addGoalForm);
      const button = addGoalForm.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        const updated = await api(`/api/treatment-plans/${plan.id}/goals`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ description: data.get("description"), interventions: data.get("interventions") || null }),
        });
        renderPlanDetail(updated);
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

  // ================= DOCUMENTS =================

  function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / 1024 / 1024).toFixed(1) + " MB";
  }

  function readFileAsBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result.split(",")[1]);
      reader.onerror = () => reject(new Error("Could not read that file"));
      reader.readAsDataURL(file);
    });
  }

  async function downloadDocument(id, fileName) {
    try {
      const res = await fetch(API_BASE + "/api/documents/" + id, { headers: authHeaders() });
      if (!res.ok) throw new Error();
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (_) {
      alert("Could not download that file.");
    }
  }

  let documentsSelectedClientId = null;

  function initDocumentsPanel(role) {
    const contextEl = document.getElementById("documents-context");
    const clientField = document.getElementById("documents-client-field");
    const form = document.getElementById("document-upload-form");
    const submitBtn = document.getElementById("document-upload-submit");
    const errorEl = form.querySelector("[data-error]");
    const list = document.getElementById("documents-list");

    if (role === "CLIENT") {
      contextEl.textContent = "Files attached to your record — consent forms, referral letters, and anything you'd like your care team to have.";
      clientField.hidden = true;
      loadDocuments(role, null);
    } else {
      contextEl.textContent = "Upload and review files attached to a client's record.";
      clientField.hidden = false;
      submitBtn.disabled = true;
      document.getElementById("documents-client-select").addEventListener("change", (e) => {
        documentsSelectedClientId = e.target.value || null;
        submitBtn.disabled = !documentsSelectedClientId;
        if (documentsSelectedClientId) {
          loadDocuments(role, documentsSelectedClientId);
        } else {
          list.innerHTML = '<p class="empty-note">Choose a client to see their files.</p>';
        }
      });
      list.innerHTML = '<p class="empty-note">Choose a client to see their files.</p>';
    }

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const file = form.querySelector("input[type=file]").files[0];
      if (!file) return;
      submitBtn.disabled = true;
      try {
        const base64Content = await readFileAsBase64(file);
        const body = {
          fileName: file.name,
          contentType: file.type || "application/octet-stream",
          base64Content,
          description: new FormData(form).get("description") || null,
        };
        if (role !== "CLIENT") body.clientId = documentsSelectedClientId;
        await api("/api/documents", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        form.reset();
        loadDocuments(role, role === "CLIENT" ? null : documentsSelectedClientId);
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        submitBtn.disabled = role !== "CLIENT" && !documentsSelectedClientId;
      }
    });

    list.addEventListener("click", async (e) => {
      const card = e.target.closest("[data-id]");
      if (!card) return;
      const id = card.dataset.id;
      if (e.target.closest("[data-action='download']")) {
        downloadDocument(id, card.dataset.filename);
      } else if (e.target.closest("[data-action='delete']")) {
        if (!confirm("Remove this document?")) return;
        try {
          await api(`/api/documents/${id}`, { method: "DELETE" });
          loadDocuments(role, role === "CLIENT" ? null : documentsSelectedClientId);
        } catch (err) {
          alert(err.message);
        }
      }
    });
  }

  async function loadDocuments(role, clientId) {
    const list = document.getElementById("documents-list");
    list.innerHTML = '<p class="empty-note">Loading…</p>';
    try {
      const path = role === "CLIENT" ? "/api/documents/mine" : `/api/documents/client/${clientId}`;
      const docs = await api(path);
      renderList(list, docs, "No documents yet.", (d) => `
        <div class="appointment-card" data-id="${d.id}" data-filename="${escapeHtml(d.fileName)}">
          <div class="row">
            <span class="who">${escapeHtml(d.fileName)}</span>
            <span class="when">${formatFileSize(d.fileSize)}</span>
          </div>
          ${d.description ? `<div class="when">${escapeHtml(d.description)}</div>` : ""}
          <div class="when">Uploaded by ${escapeHtml(d.uploadedByName)} · ${formatWhen(d.createdAt)}</div>
          <div class="actions">
            <button class="mini-button mini-button--accent" data-action="download">Download</button>
            ${d.uploadedById === currentUser.id ? '<button class="mini-button mini-button--danger" data-action="delete">Delete</button>' : ""}
          </div>
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load documents.</p>';
    }
  }

  // ================= MESSAGES =================

  let currentUser = null;
  let activeConversationId = null;

  function initMessagesPanel(role) {
    const label = document.getElementById("message-partner-label");
    label.textContent = role === "CLIENT" ? "Message your therapist" : "Message a client";

    const select = document.getElementById("message-partner-select");
    api("/api/appointments/mine")
      .then((appts) => {
        const seen = new Map();
        appts.forEach((a) => {
          if (role === "CLIENT") seen.set(a.therapistId, a.therapistName);
          else seen.set(a.clientId, a.clientName);
        });
        select.innerHTML = '<option value="">Choose…</option>' +
          [...seen.entries()].map(([id, name]) => `<option value="${id}">${escapeHtml(name)}</option>`).join("");
      })
      .catch(() => {});

    select.addEventListener("change", async () => {
      const otherUserId = select.value;
      if (!otherUserId) return;
      select.value = "";
      try {
        const conv = await api("/api/conversations", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ otherUserId }),
        });
        await loadConversations(role);
        openConversation(conv.id, role);
      } catch (err) {
        alert(err.message);
      }
    });

    document.getElementById("conversation-list").addEventListener("click", (e) => {
      const card = e.target.closest("[data-id]");
      if (!card) return;
      openConversation(card.dataset.id, role);
    });

    loadConversations(role);
  }

  async function loadConversations(role) {
    const list = document.getElementById("conversation-list");
    try {
      const conversations = await api("/api/conversations/mine");
      renderList(list, conversations, "No conversations yet.", (c) => {
        const otherName = role === "CLIENT" ? c.therapistName : c.clientName;
        const classes = ["appointment-card", "conversation-card"];
        if (c.id === activeConversationId) classes.push("selected");
        return `
          <div class="${classes.join(" ")}" data-id="${c.id}">
            <div class="row">
              <span class="who">${escapeHtml(otherName)}</span>
              ${c.unreadCount > 0 ? `<span class="unread-dot" title="${c.unreadCount} unread"></span>` : ""}
            </div>
            <div class="when">Started ${formatWhen(c.createdAt)}</div>
          </div>`;
      });
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load conversations.</p>';
    }
  }

  function renderThread(messages) {
    const threadEl = document.getElementById("message-thread");
    if (!threadEl) return;
    threadEl.innerHTML = messages.length
      ? messages.map((m) => {
          const mine = m.senderId === currentUser.id;
          return `
            <div class="message-bubble${mine ? " message-bubble--mine" : ""}">
              <div class="meta">${mine ? "You" : escapeHtml(m.senderName)} · ${formatWhen(m.sentAt)}</div>
              <div>${escapeHtml(m.body)}</div>
            </div>`;
        }).join("")
      : '<p class="empty-note">No messages yet — say hello.</p>';
    threadEl.scrollTop = threadEl.scrollHeight;
  }

  async function openConversation(conversationId, role) {
    activeConversationId = conversationId;
    const wrap = document.getElementById("thread-wrap");
    wrap.innerHTML = '<p class="empty-note">Loading…</p>';
    let messages;
    try {
      messages = await api(`/api/conversations/${conversationId}/messages`);
    } catch (_) {
      wrap.innerHTML = '<p class="empty-note">Could not load this conversation.</p>';
      return;
    }

    wrap.innerHTML = `
      <h2>Conversation</h2>
      <div class="message-thread" id="message-thread"></div>
      <form class="message-composer" id="message-composer" novalidate>
        <input type="text" name="body" placeholder="Write a message…" maxlength="4000" required>
        <button type="submit" class="mini-button mini-button--accent">Send</button>
      </form>
      <p class="form-error" data-error hidden></p>
    `;
    renderThread(messages);

    const composer = document.getElementById("message-composer");
    const errorEl = wrap.querySelector("[data-error]");
    composer.addEventListener("submit", async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const body = composer.body.value.trim();
      if (!body) return;
      const button = composer.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        await api(`/api/conversations/${conversationId}/messages`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ body }),
        });
        composer.reset();
        renderThread(await api(`/api/conversations/${conversationId}/messages`));
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      } finally {
        button.disabled = false;
      }
    });

    loadConversations(role);
  }

  // ================= BILLING =================

  function invoiceStatusClass(status) {
    if (status === "PAID") return "status-badge--approved";
    if (status === "CANCELLED") return "status-badge--cancelled";
    return "status-badge--pending";
  }

  function invoiceStatusLabel(status) {
    return status.toLowerCase().replace("_", " ");
  }

  function invoiceItemsList(items) {
    return `<div class="appointment-list" style="margin-bottom:14px">
      ${items.map((it) => `
        <div class="appointment-card">
          <div class="row"><span class="who">${escapeHtml(it.description)}</span><span class="when">${it.amount} × ${it.quantity}</span></div>
        </div>`).join("")}
    </div>`;
  }

  async function viewReceipt(invoiceId, paymentId, slot) {
    try {
      const receipt = await api(`/api/invoices/${invoiceId}/payments/${paymentId}/receipt`);
      slot.innerHTML = `<div class="receipt-box"><div class="receipt-number">${escapeHtml(receipt.receiptNumber)}</div>${receipt.amount} via ${receipt.method.toLowerCase()} · ${formatWhen(receipt.paidAt)}</div>`;
    } catch (_) {
      slot.innerHTML = '<p class="empty-note">Could not load receipt.</p>';
    }
  }

  function paymentsListHtml(payments) {
    if (!payments.length) return '<p class="empty-note">No payments recorded yet.</p>';
    return payments.map((p) => `
      <div class="appointment-card" data-payment-id="${p.id}">
        <div class="row"><span class="who">${p.amount} · ${escapeHtml(p.method)}</span><span class="when">${formatWhen(p.paidAt)}</span></div>
        <div class="when">by ${escapeHtml(p.recordedByName)}${p.reference ? " · " + escapeHtml(p.reference) : ""}</div>
        <div class="actions"><button class="mini-button" data-action="view-receipt">View Receipt</button></div>
        <div class="receipt-slot"></div>
      </div>`).join("");
  }

  // ---- CLIENT billing ----

  function initClientBillingPanel() {
    document.getElementById("billing-invoice-list").addEventListener("click", (e) => {
      const card = e.target.closest("[data-id]");
      if (!card) return;
      loadClientInvoiceDetail(card.dataset.id);
    });
    loadClientInvoices();
  }

  async function loadClientInvoices() {
    const list = document.getElementById("billing-invoice-list");
    try {
      const invoices = await api("/api/invoices/mine");
      renderList(list, invoices, "No invoices yet.", (inv) => `
        <div class="appointment-card" data-id="${inv.id}">
          <div class="row">
            <span class="who">${escapeHtml(inv.invoiceNumber)}</span>
            <span class="status-badge ${invoiceStatusClass(inv.status)}">${invoiceStatusLabel(inv.status)}</span>
          </div>
          <div class="when">Total ${inv.total} · Balance due ${inv.balanceDue}</div>
          <div class="when">${formatWhen(inv.createdAt)}</div>
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load invoices.</p>';
    }
  }

  async function loadClientInvoiceDetail(id) {
    const wrap = document.getElementById("billing-detail-wrap");
    wrap.innerHTML = '<p class="empty-note">Loading…</p>';
    let invoice, payments;
    try {
      [invoice, payments] = await Promise.all([api(`/api/invoices/${id}`), api(`/api/invoices/${id}/payments`)]);
    } catch (_) {
      wrap.innerHTML = '<p class="empty-note">Could not load this invoice.</p>';
      return;
    }

    wrap.innerHTML = `
      <h2>${escapeHtml(invoice.invoiceNumber)}</h2>
      <p class="hero-sub" style="margin-bottom:10px">
        <span class="status-badge ${invoiceStatusClass(invoice.status)}">${invoiceStatusLabel(invoice.status)}</span>
        · Total ${invoice.total} · Paid ${invoice.amountPaid} · Balance ${invoice.balanceDue}
      </p>
      ${invoiceItemsList(invoice.items)}
      <h2>Payments</h2>
      <div class="appointment-list" id="client-payments-list">${paymentsListHtml(payments)}</div>
    `;

    document.getElementById("client-payments-list").addEventListener("click", (e) => {
      const btn = e.target.closest("[data-action='view-receipt']");
      if (!btn) return;
      const card = btn.closest("[data-payment-id]");
      viewReceipt(id, card.dataset.paymentId, card.querySelector(".receipt-slot"));
    });
  }

  // ---- FINANCE ----

  function addInvoiceItemRow() {
    const wrap = document.getElementById("invoice-items-wrap");
    const row = document.createElement("div");
    row.className = "item-row";
    row.innerHTML = `
      <input type="text" name="description" placeholder="Description" required>
      <input type="number" name="amount" placeholder="Amount" min="0" step="0.01" required>
      <input type="number" name="quantity" placeholder="Qty" min="1" value="1">
      <button type="button" class="mini-button mini-button--danger" data-action="remove-row">&times;</button>
    `;
    wrap.appendChild(row);
  }

  function initFinancePanel() {
    loadFinanceReports();
    loadServices();
    loadFinanceInvoices();

    api("/api/invoices/clients").then((clients) => {
      const select = document.getElementById("invoice-client-select");
      select.innerHTML = '<option value="">Choose a client…</option>' +
        clients.map((c) => `<option value="${c.id}">${escapeHtml(c.fullName)}</option>`).join("");
    }).catch(() => {});

    document.getElementById("add-invoice-item").addEventListener("click", addInvoiceItemRow);
    addInvoiceItemRow();

    document.getElementById("invoice-items-wrap").addEventListener("click", (e) => {
      const btn = e.target.closest("[data-action='remove-row']");
      if (!btn) return;
      const wrap = document.getElementById("invoice-items-wrap");
      if (wrap.children.length > 1) btn.closest(".item-row").remove();
    });

    const invoiceForm = document.getElementById("invoice-form");
    const invoiceError = invoiceForm.querySelector("[data-error]");
    invoiceForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      invoiceError.hidden = true;
      const clientId = document.getElementById("invoice-client-select").value;
      if (!clientId) return;
      const items = [...document.querySelectorAll("#invoice-items-wrap .item-row")].map((row) => ({
        description: row.querySelector('[name="description"]').value,
        amount: Number(row.querySelector('[name="amount"]').value),
        quantity: Number(row.querySelector('[name="quantity"]').value) || 1,
      }));
      const discount = Number(new FormData(invoiceForm).get("discount")) || 0;
      const button = invoiceForm.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        await api("/api/invoices", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ clientId, items, discount }),
        });
        invoiceForm.reset();
        document.getElementById("invoice-items-wrap").innerHTML = "";
        addInvoiceItemRow();
        loadFinanceInvoices();
        loadFinanceReports();
      } catch (err) {
        invoiceError.textContent = err.message;
        invoiceError.hidden = false;
      } finally {
        button.disabled = false;
      }
    });

    const serviceForm = document.getElementById("service-form");
    const serviceError = serviceForm.querySelector("[data-error]");
    serviceForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      serviceError.hidden = true;
      const data = new FormData(serviceForm);
      const button = serviceForm.querySelector("button[type=submit]");
      button.disabled = true;
      try {
        await api("/api/services", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: data.get("name"), price: Number(data.get("price")) }),
        });
        serviceForm.reset();
        loadServices();
      } catch (err) {
        serviceError.textContent = err.message;
        serviceError.hidden = false;
      } finally {
        button.disabled = false;
      }
    });

    document.getElementById("finance-invoice-list").addEventListener("click", async (e) => {
      const card = e.target.closest("[data-id]");
      if (!card) return;
      const id = card.dataset.id;
      if (e.target.closest("[data-action='cancel-invoice']")) {
        if (!confirm("Cancel this invoice?")) return;
        try {
          await api(`/api/invoices/${id}/cancel`, { method: "PATCH" });
          loadFinanceInvoices();
          loadFinanceReports();
        } catch (err) {
          alert(err.message);
        }
        return;
      }
      loadFinanceInvoiceDetail(id);
    });
  }

  async function loadFinanceReports() {
    try {
      const [finance, operational] = await Promise.all([api("/api/reports/finance"), api("/api/reports/operational")]);
      const statRow = document.getElementById("finance-stat-row");
      statRow.innerHTML = "";
      [
        ["Invoiced", finance.totalInvoiced],
        ["Collected", finance.totalCollected],
        ["Outstanding", finance.totalOutstanding],
        ["Clients", operational.totalClients],
        ["Therapists", operational.totalTherapists],
      ].forEach(([label, value]) => {
        const tile = document.createElement("div");
        tile.className = "stat-tile";
        tile.innerHTML = `<div class="num">${value}</div><div class="lbl">${label}</div>`;
        statRow.appendChild(tile);
      });
    } catch (_) {
      /* leave stats as-is on failure */
    }
  }

  async function loadServices() {
    const list = document.getElementById("service-list");
    try {
      const services = await api("/api/services");
      renderList(list, services, "No services yet.", (s) => `
        <div class="appointment-card">
          <div class="row"><span class="who">${escapeHtml(s.name)}</span><span class="when">${s.price}</span></div>
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load services.</p>';
    }
  }

  async function loadFinanceInvoices() {
    const list = document.getElementById("finance-invoice-list");
    try {
      const invoices = await api("/api/invoices");
      renderList(list, invoices, "No invoices yet.", (inv) => `
        <div class="appointment-card" data-id="${inv.id}">
          <div class="row">
            <span class="who">${escapeHtml(inv.invoiceNumber)} — ${escapeHtml(inv.clientName)}</span>
            <span class="status-badge ${invoiceStatusClass(inv.status)}">${invoiceStatusLabel(inv.status)}</span>
          </div>
          <div class="when">Total ${inv.total} · Balance due ${inv.balanceDue}</div>
          <div class="actions">
            <button class="mini-button" data-action="view">View</button>
            ${inv.status === "UNPAID" && Number(inv.amountPaid) === 0 ? '<button class="mini-button mini-button--danger" data-action="cancel-invoice">Cancel</button>' : ""}
          </div>
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load invoices.</p>';
    }
  }

  async function loadFinanceInvoiceDetail(id) {
    const wrap = document.getElementById("finance-invoice-detail");
    wrap.innerHTML = '<p class="empty-note">Loading…</p>';
    let invoice, payments;
    try {
      [invoice, payments] = await Promise.all([api(`/api/invoices/${id}`), api(`/api/invoices/${id}/payments`)]);
    } catch (_) {
      wrap.innerHTML = '<p class="empty-note">Could not load this invoice.</p>';
      return;
    }

    const canPay = Number(invoice.balanceDue) > 0 && invoice.status !== "CANCELLED";

    wrap.innerHTML = `
      <h2 style="margin-top:22px">${escapeHtml(invoice.invoiceNumber)} — ${escapeHtml(invoice.clientName)}</h2>
      <p class="hero-sub" style="margin-bottom:10px">
        <span class="status-badge ${invoiceStatusClass(invoice.status)}">${invoiceStatusLabel(invoice.status)}</span>
        · Total ${invoice.total} · Paid ${invoice.amountPaid} · Balance ${invoice.balanceDue}
      </p>
      ${invoiceItemsList(invoice.items)}
      ${canPay ? `
        <form class="auth-form" id="record-payment-form" novalidate style="margin-bottom:14px">
          <label class="field"><span>Amount</span><input type="number" name="amount" min="0.01" step="0.01" max="${invoice.balanceDue}" required></label>
          <label class="field"><span>Method</span>
            <select name="method">
              <option value="CASH">Cash</option>
              <option value="CARD">Card</option>
              <option value="BANK_TRANSFER">Bank Transfer</option>
              <option value="MOBILE_MONEY">Mobile Money</option>
              <option value="OTHER">Other</option>
            </select>
          </label>
          <label class="field"><span>Reference (optional)</span><input type="text" name="reference"></label>
          <p class="form-error" data-error hidden></p>
          <button type="submit" class="mini-button mini-button--accent">Record Payment</button>
        </form>` : ""}
      <h2>Payments</h2>
      <div class="appointment-list" id="finance-payments-list">${paymentsListHtml(payments)}</div>
    `;

    const paymentForm = document.getElementById("record-payment-form");
    if (paymentForm) {
      const errorEl = paymentForm.querySelector("[data-error]");
      paymentForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        errorEl.hidden = true;
        const data = new FormData(paymentForm);
        const button = paymentForm.querySelector("button[type=submit]");
        button.disabled = true;
        try {
          await api(`/api/invoices/${id}/payments`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              amount: Number(data.get("amount")),
              method: data.get("method"),
              reference: data.get("reference") || null,
            }),
          });
          loadFinanceInvoiceDetail(id);
          loadFinanceInvoices();
          loadFinanceReports();
        } catch (err) {
          errorEl.textContent = err.message;
          errorEl.hidden = false;
        } finally {
          button.disabled = false;
        }
      });
    }

    document.getElementById("finance-payments-list").addEventListener("click", (e) => {
      const btn = e.target.closest("[data-action='view-receipt']");
      if (!btn) return;
      const card = btn.closest("[data-payment-id]");
      viewReceipt(id, card.dataset.paymentId, card.querySelector(".receipt-slot"));
    });
  }

  // ================= MAINTENANCE =================

  function initMaintenancePanel() {
    loadClinicOverview();
    loadMaintenanceAppointments();
    loadAuditLog();

    document.getElementById("user-table-body").addEventListener("click", async (e) => {
      const superAdminBtn = e.target.closest("[data-action='grant-super-admin'], [data-action='revoke-super-admin']");
      const enabledBtn = e.target.closest("[data-action='enable-user'], [data-action='disable-user']");
      const btn = superAdminBtn || enabledBtn;
      if (!btn) return;
      const id = btn.closest("[data-id]").dataset.id;
      btn.disabled = true;
      try {
        if (superAdminBtn) {
          const grant = btn.dataset.action === "grant-super-admin";
          if (!confirm(grant ? "Grant super-admin access to this account?" : "Revoke super-admin access from this account?")) {
            btn.disabled = false;
            return;
          }
          await api(`/api/staff/${id}/super-admin`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ superAdmin: grant }),
          });
        } else {
          const enable = btn.dataset.action === "enable-user";
          if (!enable && !confirm("Disable this account? They won't be able to log in until re-enabled.")) {
            btn.disabled = false;
            return;
          }
          await api(`/api/staff/${id}/enabled`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ enabled: enable }),
          });
        }
        loadClinicOverview();
      } catch (err) {
        alert(err.message);
        btn.disabled = false;
      }
    });

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

      const superAdminCol = document.getElementById("super-admin-col");
      superAdminCol.hidden = !currentUser.superAdmin;

      const body = document.getElementById("user-table-body");
      body.innerHTML = "";
      users.forEach((u) => {
        const row = document.createElement("tr");
        row.dataset.id = u.id;
        const statusBits = [];
        if (!u.enabled) statusBits.push('<span class="status-badge status-badge--declined">disabled</span>');
        else if (u.locked) statusBits.push('<span class="status-badge status-badge--pending">locked</span>');
        else statusBits.push('<span class="status-badge status-badge--approved">active</span>');
        const canToggle = u.id !== currentUser.id;
        row.innerHTML =
          "<td>" + escapeHtml(u.fullName) + "</td>" +
          "<td>" + escapeHtml(u.email) + "</td>" +
          "<td>" + ROLE_LABEL[u.role] + "</td>" +
          `<td data-id="${u.id}">${statusBits.join(" ")}${canToggle
            ? (u.enabled
                ? ' <button class="mini-button mini-button--danger" data-action="disable-user">Disable</button>'
                : ' <button class="mini-button mini-button--accent" data-action="enable-user">Enable</button>')
            : ""}</td>` +
          (currentUser.superAdmin
            ? `<td data-id="${u.id}">${u.superAdmin
                ? '<button class="mini-button mini-button--danger" data-action="revoke-super-admin">Revoke</button>'
                : (canToggle ? '<button class="mini-button" data-action="grant-super-admin">Grant</button>' : "")}</td>`
            : "");
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

  async function loadAuditLog() {
    const list = document.getElementById("audit-log-list");
    try {
      const entries = await api("/api/audit-logs?size=50");
      renderList(list, entries, "No activity recorded yet.", (e) => `
        <div class="appointment-card">
          <div class="row">
            <span class="who">${escapeHtml(e.action)}</span>
            <span class="status-badge ${e.result === "SUCCESS" ? "status-badge--approved" : "status-badge--declined"}">${e.result.toLowerCase()}</span>
          </div>
          <div class="when">${escapeHtml(e.actorName)} · ${formatWhen(e.createdAt)}</div>
          ${e.recordType ? `<div class="when">${escapeHtml(e.recordType)}${e.recordId ? " · " + escapeHtml(e.recordId) : ""}</div>` : ""}
          ${e.detail ? `<div class="when">${escapeHtml(e.detail)}</div>` : ""}
        </div>`);
    } catch (_) {
      list.innerHTML = '<p class="empty-note">Could not load the audit log.</p>';
    }
  }

  // ================= SUPER ADMIN ROLE SWITCHER =================

  function initRoleSwitcher(user) {
    if (!user.superAdmin) return;
    const select = document.getElementById("role-switcher");
    const roles = ["CLIENT", "THERAPIST", "CLINICAL_SUPERVISOR", "RECEPTIONIST", "FINANCE", "MAINTENANCE"];
    select.innerHTML = roles.map((r) => `<option value="${r}">Acting as ${ROLE_LABEL[r]}</option>`).join("");
    select.value = user.role;
    select.hidden = false;
    select.addEventListener("change", async () => {
      const chosen = select.value;
      try {
        await api("/api/auth/active-role", {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ role: chosen === user.trueRole ? null : chosen }),
        });
        window.location.reload();
      } catch (err) {
        alert(err.message);
        select.value = user.role;
      }
    });
  }

  loadCurrentUser().then((user) => {
    if (user) {
      currentUser = user;
      renderUser(user);
      initNotifications();
      initRoleSwitcher(user);
    }
  });
})();
