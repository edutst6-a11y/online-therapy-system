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

  const ROLE_COPY = {
    CLIENT: "Session booking and appointment tracking will live here next — for now you're signed in as a client.",
    THERAPIST: "This is where you'll publish your availability and manage the patients assigned to you.",
    RECEPTIONIST: "This is where you'll triage incoming appointment requests — approve, reschedule, or decline.",
    MAINTENANCE: "You have clinic-wide visibility: every user, every appointment, and the ability to provision new staff accounts below.",
  };

  const ROLE_LABEL = {
    CLIENT: "Client",
    THERAPIST: "Therapist",
    RECEPTIONIST: "Receptionist",
    MAINTENANCE: "Maintenance",
  };

  async function loadCurrentUser() {
    const res = await fetch(API_BASE + "/api/auth/me", { headers: authHeaders() });
    if (!res.ok) {
      clearSessionAndRedirect();
      return null;
    }
    return res.json();
  }

  function renderUser(user) {
    document.getElementById("greeting").innerHTML =
      "HELLO,<br>" + escapeHtml(firstName(user.fullName)).toUpperCase() + ".";
    document.getElementById("role-pill").textContent = ROLE_LABEL[user.role] || user.role;
    document.getElementById("panel-text").textContent = ROLE_COPY[user.role] || "";

    if (user.role === "MAINTENANCE") {
      document.getElementById("maintenance-panel").hidden = false;
      loadClinicOverview();
    }
  }

  function firstName(fullName) {
    return (fullName || "").split(" ")[0] || "there";
  }

  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str;
    return div.innerHTML;
  }

  async function loadClinicOverview() {
    const res = await fetch(API_BASE + "/api/staff", { headers: authHeaders() });
    if (!res.ok) return;
    const users = await res.json();

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
  }

  document.getElementById("logout-btn").addEventListener("click", clearSessionAndRedirect);

  const staffForm = document.getElementById("staff-form");
  staffForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const errorEl = staffForm.querySelector("[data-error]");
    errorEl.hidden = true;

    const data = new FormData(staffForm);
    const button = staffForm.querySelector("button[type=submit]");
    button.disabled = true;

    try {
      const res = await fetch(API_BASE + "/api/staff", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders() },
        body: JSON.stringify({
          fullName: data.get("fullName"),
          email: data.get("email"),
          password: data.get("password"),
          role: data.get("role"),
        }),
      });

      const body = await res.json().catch(() => null);

      if (!res.ok) {
        throw new Error((body && body.error) || "Could not create the account.");
      }

      staffForm.reset();
      await loadClinicOverview();
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    } finally {
      button.disabled = false;
    }
  });

  loadCurrentUser().then((user) => {
    if (user) renderUser(user);
  });
})();
