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
    RECEPTIONIST: "Incoming requests need your approval, a reschedule, or a decline.",
    MAINTENANCE: "You have clinic-wide visibility: every user, every appointment, and the ability to provision new staff accounts below.",
  };

  const ROLE_LABEL = {
    CLIENT: "Client",
    THERAPIST: "Therapist",
    RECEPTIONIST: "Receptionist",
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

  function appointmentCard(appt, { personLabel, personName, actions }) {
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
      document.getElementById("client-panel").hidden = false;
      initClientPanel();
    } else if (user.role === "THERAPIST") {
      document.getElementById("therapist-panel").hidden = false;
      initTherapistPanel();
    } else if (user.role === "RECEPTIONIST") {
      document.getElementById("receptionist-panel").hidden = false;
      initReceptionistPanel();
    } else if (user.role === "MAINTENANCE") {
      document.getElementById("maintenance-panel").hidden = false;
      initMaintenancePanel();
    }
  }

  document.getElementById("logout-btn").addEventListener("click", clearSessionAndRedirect);

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
      const btn = e.target.closest("[data-action='complete']");
      if (!btn) return;
      btn.disabled = true;
      try {
        await api(`/api/appointments/${btn.closest(".appointment-card").dataset.id}/complete`, { method: "PATCH" });
        loadTherapistAppointments();
      } catch (_) {
        btn.disabled = false;
      }
    });

    loadTherapistSlots();
    loadTherapistAppointments();
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
      renderList(container, appts, "No sessions booked with you yet.", (appt) => appointmentCard(appt, {
        personLabel: "Client",
        personName: appt.clientName,
        actions: appt.status === "APPROVED"
          ? '<button class="mini-button" data-action="complete">Mark Complete</button>' : "",
      }));
    } catch (_) {
      container.innerHTML = '<p class="empty-note">Could not load your sessions.</p>';
    }
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
      ["CLIENT", "THERAPIST", "RECEPTIONIST", "MAINTENANCE"].forEach((role) => {
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
