// dash.js (perbaikan: semua DOM access diletakkan setelah DOMContentLoaded, safety checks)

// Helper CSRF global. Token disimpan Spring Security di cookie XSRF-TOKEN yang
// sengaja tidak httpOnly, supaya form bentukan JavaScript dan pemanggilan fetch
// bisa menyertakannya. Form yang dirender Thymeleaf sudah otomatis dapat token.
window.csrfToken = function () {
    var cocok = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return cocok ? decodeURIComponent(cocok[1]) : null;
};

window.csrfHeaders = function (tambahan) {
    var headers = tambahan || {};
    var token = window.csrfToken();
    if (token) {
        headers['X-XSRF-TOKEN'] = token;
    }
    return headers;
};

document.addEventListener('DOMContentLoaded', function () {

    // Elemen UI (ambil setelah DOM siap)
    const sidebarToggleBtns = document.querySelectorAll(".sidebar-toggle");
    const sidebar = document.querySelector(".sidebar");
    const sidebarBackdrop = document.querySelector(".sidebar-backdrop");
    const themeToggleBtn = document.querySelector(".theme-toggle");
    const themeIcon = themeToggleBtn ? themeToggleBtn.querySelector(".theme-icon") : null;
    const themeText = themeToggleBtn ? themeToggleBtn.querySelector(".theme-text") : null;
    const menuLinks = document.querySelectorAll(".menu-link");

    // Akses keyboard: sediakan jalan pintas ke konten utama pada seluruh halaman
    // dashboard tanpa harus menduplikasi markup di setiap template lama.
    const mainContent = document.querySelector("main, .main-content");
    if (mainContent && !document.querySelector(".skip-link")) {
        if (!mainContent.id) mainContent.id = "main-content";
        mainContent.setAttribute("tabindex", "-1");
        const skipLink = document.createElement("a");
        skipLink.className = "skip-link";
        skipLink.href = `#${mainContent.id}`;
        skipLink.textContent = "Lewati ke konten utama";
        document.body.insertBefore(skipLink, document.body.firstChild);
    }

    document.querySelectorAll("button[title]:not([aria-label])").forEach((button) => {
        button.setAttribute("aria-label", button.getAttribute("title"));
    });

    const modals = document.querySelectorAll(".modal");
    modals.forEach((modal, index) => {
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");
        const heading = modal.querySelector("h1, h2, h3, .modal-title");
        if (heading) {
            if (!heading.id) heading.id = `modal-title-${index + 1}`;
            modal.setAttribute("aria-labelledby", heading.id);
        }

        const hidden = modal.classList.contains("hidden") || modal.getAttribute("aria-hidden") === "true";
        modal.setAttribute("aria-hidden", String(hidden));
        modal.inert = hidden;
    });

    // Pengelola modal bersama: mengisolasi konten latar, menjebak fokus di
    // dialog, menutup dengan Escape, lalu mengembalikan fokus ke pemicunya.
    const focusableSelector = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled]):not([type="hidden"])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])'
    ].join(',');
    const modalStates = new WeakMap();
    let activeModal = null;

    const resolveModal = (modalOrId) => {
        if (typeof modalOrId === "string") return document.getElementById(modalOrId);
        return modalOrId instanceof HTMLElement ? modalOrId : null;
    };

    const getFocusableElements = (modal) => Array.from(modal.querySelectorAll(focusableSelector))
        .filter((element) => element.getClientRects().length > 0 && element.getAttribute("aria-hidden") !== "true");

    const isolateModalBackground = (modal) => {
        const background = [];
        let branch = modal;
        let parent = branch.parentElement;

        // Modal lama berada di dalam .main-content, bukan langsung di <body>.
        // Karena itu sibling di setiap tingkat perlu dinonaktifkan tanpa ikut
        // membuat ancestor modal menjadi inert.
        while (parent && parent !== document.documentElement) {
            Array.from(parent.children).forEach((sibling) => {
                if (sibling === branch || ["SCRIPT", "STYLE", "LINK"].includes(sibling.tagName)) return;
                background.push({
                    element: sibling,
                    hadInert: sibling.hasAttribute("inert"),
                    ariaHidden: sibling.getAttribute("aria-hidden")
                });
                sibling.inert = true;
                sibling.setAttribute("aria-hidden", "true");
            });
            branch = parent;
            parent = parent.parentElement;
        }

        return background;
    };

    const restoreModalBackground = (background) => {
        (background || []).forEach(({ element, hadInert, ariaHidden }) => {
            if (hadInert) {
                element.inert = true;
                element.setAttribute("inert", "");
            } else {
                element.inert = false;
                element.removeAttribute("inert");
            }

            if (ariaHidden === null) element.removeAttribute("aria-hidden");
            else element.setAttribute("aria-hidden", ariaHidden);
        });
    };

    const openAccessibleModal = (modalOrId, options = {}) => {
        const modal = resolveModal(modalOrId);
        if (!modal) return;

        if (activeModal === modal) {
            const currentFocusable = getFocusableElements(modal)[0] || modal.querySelector(".modal-content") || modal;
            currentFocusable.focus({ preventScroll: true });
            return;
        }

        if (activeModal && activeModal !== modal) {
            window.SipatikModal.close(activeModal, { restoreFocus: false });
        }

        const trigger = options.trigger instanceof HTMLElement ? options.trigger : document.activeElement;
        activeModal = modal;
        modal.inert = false;
        modal.classList.remove("hidden");
        modal.style.removeProperty("display");
        modal.setAttribute("aria-hidden", "false");
        document.body.classList.add("modal-open");

        let initialFocus = options.initialFocus;
        if (typeof initialFocus === "string") initialFocus = modal.querySelector(initialFocus);
        if (!(initialFocus instanceof HTMLElement)) {
            initialFocus = modal.querySelector('[autofocus], [data-modal-initial-focus]') || getFocusableElements(modal)[0];
        }
        const focusTarget = initialFocus || modal.querySelector(".modal-content") || modal;
        if (!focusTarget.hasAttribute("tabindex") && focusTarget === modal.querySelector(".modal-content")) {
            focusTarget.setAttribute("tabindex", "-1");
        }

        // Pindahkan fokus sebelum ancestor tombol pemicu diberi aria-hidden;
        // browser modern sengaja menolak aria-hidden pada elemen yang masih fokus.
        focusTarget.focus({ preventScroll: true });
        modalStates.set(modal, {
            trigger: trigger instanceof HTMLElement ? trigger : null,
            background: isolateModalBackground(modal)
        });

        // Ulangi sesudah layout agar elemen tetap menerima fokus pada browser
        // yang menunda rendering dialog sampai frame berikutnya.
        window.requestAnimationFrame(() => focusTarget.focus({ preventScroll: true }));
    };

    const closeAccessibleModal = (modalOrId, options = {}) => {
        const modal = resolveModal(modalOrId);
        if (!modal) return;

        const state = modalStates.get(modal);
        if (modal.contains(document.activeElement) && typeof document.activeElement.blur === "function") {
            document.activeElement.blur();
        }
        modal.classList.add("hidden");
        modal.setAttribute("aria-hidden", "true");
        modal.inert = true;
        restoreModalBackground(state && state.background);
        modalStates.delete(modal);

        if (activeModal === modal) activeModal = null;
        if (!activeModal) document.body.classList.remove("modal-open");
        modal.dispatchEvent(new CustomEvent("sipatik:modal-close"));

        if (options.restoreFocus !== false && state && state.trigger && state.trigger.isConnected
            && !state.trigger.closest("[inert]")) {
            state.trigger.focus({ preventScroll: true });
        }
    };

    window.SipatikModal = {
        open: openAccessibleModal,
        close: closeAccessibleModal,
        active: () => activeModal
    };

    const lockSubmitButton = (form, busyLabel = "Menyimpan...") => {
        if (!form || form.dataset.submitting === "true") return false;

        form.dataset.submitting = "true";
        const submitButton = form.querySelector('button[type="submit"], input[type="submit"]');
        if (submitButton) {
            submitButton.dataset.submitLocked = "true";
            submitButton.dataset.idleLabel = submitButton instanceof HTMLInputElement
                ? submitButton.value
                : submitButton.textContent;
            submitButton.disabled = true;
            submitButton.setAttribute("aria-busy", "true");
            if (submitButton instanceof HTMLInputElement) submitButton.value = busyLabel;
            else submitButton.textContent = busyLabel;
        }
        return true;
    };

    window.SipatikForm = { lockSubmit: lockSubmitButton };

    // Browser dapat memulihkan halaman dari back-forward cache. Pulihkan hanya
    // tombol yang dikunci oleh helper ini agar form tetap bisa dikirim ulang.
    window.addEventListener("pageshow", () => {
        document.querySelectorAll('[data-submit-locked="true"]').forEach((submitButton) => {
            submitButton.disabled = false;
            submitButton.removeAttribute("aria-busy");
            if (submitButton instanceof HTMLInputElement) submitButton.value = submitButton.dataset.idleLabel || "Kirim";
            else submitButton.textContent = submitButton.dataset.idleLabel || "Kirim";
            delete submitButton.dataset.submitLocked;
            delete submitButton.dataset.idleLabel;
            if (submitButton.form) delete submitButton.form.dataset.submitting;
        });
    });

    // Kompatibilitas untuk fungsi modal lama di template yang masih hanya
    // menambah/menghapus class `hidden`. Perubahan class itu diterjemahkan ke
    // pengelola aksesibel di atas tanpa perlu menduplikasi logika tiap halaman.
    const legacyModalObserver = new MutationObserver((records) => {
        records.forEach(({ target: modal }) => {
            const hiddenByClass = modal.classList.contains("hidden");
            const hiddenForA11y = modal.getAttribute("aria-hidden") === "true";
            if (!hiddenByClass && hiddenForA11y) {
                openAccessibleModal(modal, { trigger: document.activeElement });
            } else if (hiddenByClass && !hiddenForA11y) {
                closeAccessibleModal(modal);
            }
        });
    });
    modals.forEach((modal) => legacyModalObserver.observe(modal, {
        attributes: true,
        attributeFilter: ["class"]
    }));

    document.addEventListener("keydown", (event) => {
        if (!activeModal) return;

        if (event.key === "Escape") {
            event.preventDefault();
            const requestClose = new CustomEvent("sipatik:modal-request-close", { cancelable: true });
            if (activeModal.dispatchEvent(requestClose)) closeAccessibleModal(activeModal);
            return;
        }

        if (event.key !== "Tab") return;
        const focusable = getFocusableElements(activeModal);
        if (focusable.length === 0) {
            event.preventDefault();
            (activeModal.querySelector(".modal-content") || activeModal).focus();
            return;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!activeModal.contains(document.activeElement)) {
            event.preventDefault();
            (event.shiftKey ? last : first).focus();
        } else if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }, true);

    // Helper: update theme icon (cek keberadaan themeIcon dan sidebar)
    const updateThemeIcon = () => {
        const isDark = document.body.classList.contains("dark-theme");
        if (themeToggleBtn) {
            themeToggleBtn.type = "button";
            themeToggleBtn.setAttribute("aria-pressed", String(isDark));
            themeToggleBtn.setAttribute("aria-label", isDark ? "Gunakan tema terang" : "Gunakan tema gelap");
        }
        if (themeIcon) {
            themeIcon.textContent = isDark ? "light_mode" : "dark_mode";
        }
        if (themeText) {
            themeText.textContent = isDark ? "Mode terang" : "Mode gelap";
        }
    };

    let drawerBackgroundState = null;

    const updateDrawerBackground = (drawerOpen) => {
        if (!mainContent) return;
        if (drawerOpen && !drawerBackgroundState) {
            drawerBackgroundState = {
                hadInert: mainContent.hasAttribute("inert"),
                ariaHidden: mainContent.getAttribute("aria-hidden")
            };
            mainContent.inert = true;
            mainContent.setAttribute("aria-hidden", "true");
        } else if (!drawerOpen && drawerBackgroundState) {
            if (drawerBackgroundState.hadInert) {
                mainContent.inert = true;
                mainContent.setAttribute("inert", "");
            } else {
                mainContent.inert = false;
                mainContent.removeAttribute("inert");
            }
            if (drawerBackgroundState.ariaHidden === null) mainContent.removeAttribute("aria-hidden");
            else mainContent.setAttribute("aria-hidden", drawerBackgroundState.ariaHidden);
            drawerBackgroundState = null;
        }
    };

    const updateSidebarA11y = () => {
        if (!sidebar) return;
        if (!sidebar.id) sidebar.id = "app-sidebar";
        const collapsed = sidebar.classList.contains("collapsed");
        const mobile = window.matchMedia("(max-width: 768px)").matches;
        const mobileHidden = mobile && collapsed;
        const drawerOpen = mobile && !collapsed;
        const expanded = !collapsed;
        const focusWasInside = sidebar.contains(document.activeElement)
            || sidebarBackdrop === document.activeElement;
        const externalToggle = Array.from(sidebarToggleBtns).find((btn) => !sidebar.contains(btn));

        // Pindahkan fokus sebelum subtree dibuat inert/tersembunyi dari tree
        // aksesibilitas agar browser tidak menahan fokus pada elemen tersembunyi.
        if (mobileHidden && focusWasInside && externalToggle) {
            externalToggle.focus();
        }

        sidebar.inert = mobileHidden;
        sidebar.setAttribute("aria-hidden", String(mobileHidden));
        updateDrawerBackground(drawerOpen);
        if (sidebarBackdrop) {
            sidebarBackdrop.classList.toggle("is-visible", drawerOpen);
            sidebarBackdrop.setAttribute("aria-hidden", String(!drawerOpen));
        }
        sidebarToggleBtns.forEach((btn) => {
            btn.type = "button";
            btn.setAttribute("aria-controls", sidebar.id);
            btn.setAttribute("aria-expanded", String(expanded));
            btn.setAttribute("aria-label", expanded ? "Tutup navigasi" : "Buka navigasi");
        });

        // Ikon pada sidebar ringkas tetap mudah dikenali oleh pengguna mouse.
        menuLinks.forEach((link) => {
            const label = link.querySelector(".menu-label")?.textContent?.trim();
            if (!mobile && collapsed && label) link.setAttribute("title", label);
            else link.removeAttribute("title");
        });

    };

    // Apply dark theme if saved or system prefers, lalu update icon
    const savedTheme = localStorage.getItem("theme");
    const systemPrefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    const shouldUseDarkTheme = savedTheme === "dark" || (!savedTheme && systemPrefersDark);
    document.body.classList.toggle("dark-theme", shouldUseDarkTheme);
    updateThemeIcon();

    // Toggle theme (cek dulu tombol ada)
    if (themeToggleBtn) {
        themeToggleBtn.addEventListener("click", () => {
            const isDark = document.body.classList.toggle("dark-theme");
            localStorage.setItem("theme", isDark ? "dark" : "light");
            updateThemeIcon();
        });
    }

    // Toggle sidebar (cek ada button & sidebar)
    if (sidebar && sidebarToggleBtns && sidebarToggleBtns.length) {
        const isAdminNavigation = sidebar.classList.contains("admin-sidebar");
        const sidebarPreferenceKey = "sipatik.adminSidebar.collapsed";
        const getSavedSidebarPreference = () => isAdminNavigation
            && localStorage.getItem(sidebarPreferenceKey) === "true";

        sidebarToggleBtns.forEach((btn) => {
            btn.addEventListener("click", () => {
                sidebar.classList.toggle("collapsed");
                if (isAdminNavigation && !window.matchMedia("(max-width: 768px)").matches) {
                    localStorage.setItem(sidebarPreferenceKey, String(sidebar.classList.contains("collapsed")));
                }
                updateThemeIcon();
                updateSidebarA11y();
                if (window.matchMedia("(max-width: 768px)").matches
                    && !sidebar.classList.contains("collapsed")) {
                    const firstSidebarControl = sidebar.querySelector("[data-drawer-initial-focus]")
                        || getFocusableElements(sidebar)[0];
                    if (firstSidebarControl) {
                        window.requestAnimationFrame(() => firstSidebarControl.focus({ preventScroll: true }));
                    }
                }
            });
        });

        // Pada layar kecil sidebar adalah drawer dan harus tertutup saat halaman
        // pertama kali dibuka. Di desktop, ingat pilihan ringkas pengguna.
        let mobileLayout = window.innerWidth <= 768;
        sidebar.classList.toggle("collapsed", mobileLayout || getSavedSidebarPreference());
        updateThemeIcon();
        updateSidebarA11y();

        window.addEventListener("resize", () => {
            const nextMobileLayout = window.innerWidth <= 768;
            if (nextMobileLayout === mobileLayout) return;

            mobileLayout = nextMobileLayout;
            sidebar.classList.toggle("collapsed", mobileLayout || getSavedSidebarPreference());
            updateThemeIcon();
            updateSidebarA11y();
        });

        document.addEventListener("keydown", (event) => {
            const drawerOpen = window.matchMedia("(max-width: 768px)").matches
                && !sidebar.classList.contains("collapsed");
            if (!drawerOpen || activeModal) return;

            if (event.key === "Escape") {
                event.preventDefault();
                sidebar.classList.add("collapsed");
                updateThemeIcon();
                updateSidebarA11y();
                return;
            }

            // Sidebar mobile berperilaku sebagai drawer modal. Jaga fokus tetap
            // di dalam drawer sampai pengguna menutupnya dengan tombol atau Escape.
            if (event.key === "Tab") {
                const focusable = getFocusableElements(sidebar);
                if (!focusable.length) return;
                const first = focusable[0];
                const last = focusable[focusable.length - 1];

                if (!sidebar.contains(document.activeElement)) {
                    event.preventDefault();
                    (event.shiftKey ? last : first).focus();
                } else if (event.shiftKey && document.activeElement === first) {
                    event.preventDefault();
                    last.focus();
                } else if (!event.shiftKey && document.activeElement === last) {
                    event.preventDefault();
                    first.focus();
                }
            }
        });

        const closeMobileDrawer = () => {
            const drawerOpen = window.matchMedia("(max-width: 768px)").matches
                && !sidebar.classList.contains("collapsed");
            if (!drawerOpen) return;

            sidebar.classList.add("collapsed");
            updateThemeIcon();
            updateSidebarA11y();
        };

        sidebarBackdrop?.addEventListener("click", closeMobileDrawer);

        // Halaman lama tanpa elemen backdrop tetap dapat ditutup dengan klik area luar.
        if (!sidebarBackdrop) {
            document.body.addEventListener("click", (event) => {
                if (event.target === document.body) closeMobileDrawer();
            });
        }
    }

    // Rupiah formatter helper (digunakan di banyak tempat)
    function formatRupiahNumber(value) {
        if (value === null || value === undefined || value === '') return 'Rp 0';
        const n = (typeof value === 'number') ? value : parseFloat(String(value).replace(/[^\d.-]/g, '')) || 0;

        // Format with dots as thousands separators and comma as decimal (Indonesian format)
        const formatted = Math.abs(n).toLocaleString('id-ID', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });

        // Add negative sign if needed
        const sign = n < 0 ? '-' : '';
        return `Rp ${sign}${formatted}`;
    }

    // Fungsi untuk memformat semua elemen .format-rupiah
    function applyRupiahFormatting() {
        document.querySelectorAll('.format-rupiah').forEach(el => {
            // Nilai mentah disimpan ke data-value pada pemanggilan pertama supaya
            // pemanggilan berikutnya tidak mem-parse teks yang sudah diformat.
            if (!el.hasAttribute('data-value')) {
                el.setAttribute('data-value', el.textContent.trim());
            }
            el.textContent = formatRupiahNumber(el.getAttribute('data-value'));
        });
    }

    // Dipanggil langsung: blok ini sendiri sudah berjalan di dalam handler
    // DOMContentLoaded, jadi mendaftarkan listener DOMContentLoaded lagi di sini
    // membuat callback-nya tidak pernah dieksekusi dan semua elemen .format-rupiah
    // tertinggal tanpa format (dashboard user bahkan permanen menampilkan Rp 0).
    applyRupiahFormatting();

    // Fungsi toggle show/hide password yang aman (cek element ada)
    const passwordSide = (loginPassId, loginEyeId) => {
        const input = document.getElementById(loginPassId);
        const iconEye = document.getElementById(loginEyeId);

        if (!input || !iconEye) {
            // jika element tidak ada (halaman bukan halaman login), jangan lakukan apa-apa
            return;
        }

        const syncPasswordToggle = () => {
            const isVisible = input.type === 'text';
            iconEye.classList.toggle('ri-eye-fill', isVisible);
            iconEye.classList.toggle('ri-eye-off-fill', !isVisible);
            iconEye.setAttribute('aria-pressed', String(isVisible));
            if (!iconEye.hasAttribute('aria-controls')) {
                iconEye.setAttribute('aria-controls', input.id);
            }
            if (!iconEye.getAttribute('aria-label')) {
                iconEye.setAttribute('aria-label', 'Tampilkan password');
            }
        };

        syncPasswordToggle();
        iconEye.addEventListener('click', () => {
            input.type = input.type === 'password' ? 'text' : 'password';
            syncPasswordToggle();
        });
    };

    // Panggil hanya jika elemen ada
    passwordSide('newPassword', 'loginPassword');
    passwordSide('confirmPassword', 'loginPassword2');

    // Pengeluaran: toggle panel (cek element ada)
    const toggleBtn = document.getElementById("toggleButton");
    const inputPanel = document.getElementById("inputPanel");
    const listPanel = document.getElementById("listPanel");

    if (toggleBtn && inputPanel && listPanel) {
        toggleBtn.addEventListener("click", () => {
            inputPanel.classList.toggle("hidden");
            listPanel.classList.toggle("hidden");

            toggleBtn.textContent = listPanel.classList.contains("hidden")
                ? "Lihat Pengeluaran"
                : "Input Pengeluaran";
        });
    }

    // Elemen tabel / form pengeluaran
    const pengeluaranBody = document.getElementById("pengeluaranBody");
    const filterForm = document.getElementById("filterForm");
    const expenseEntryForm = document.querySelector("form.expense-form");

    if (expenseEntryForm) {
        // Event submit baru berjalan setelah validasi native sukses. Kunci hanya
        // tombolnya; field tetap enabled sehingga seluruh payload tetap terkirim.
        expenseEntryForm.addEventListener("submit", (event) => {
            if (!lockSubmitButton(expenseEntryForm)) event.preventDefault();
        });
    }

    function renderPengeluaranMessage(message, isError = false) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 6;
        cell.style.textAlign = "center";
        if (isError) cell.style.color = "var(--sp-danger, #c00)";
        cell.textContent = message;
        row.appendChild(cell);
        pengeluaranBody.replaceChildren(row);
    }

    function appendTextCell(row, value, className) {
        const cell = document.createElement("td");
        if (className) cell.className = className;
        cell.textContent = value === null || value === undefined ? "" : String(value);
        row.appendChild(cell);
        return cell;
    }

    async function loadPengeluaran(params = {}) {
        if (!pengeluaranBody) return;

        const query = new URLSearchParams(params).toString();
        const res = await fetch(`/admin/api/pengeluaran?${query}`);
        if (!res.ok) {
            renderPengeluaranMessage("Gagal memuat data", true);
            return;
        }

        const data = await res.json();

        pengeluaranBody.replaceChildren();

        if (!Array.isArray(data) || data.length === 0) {
            renderPengeluaranMessage("Belum ada data pengeluaran");
            return;
        }

        data.forEach(p => {
            const id = String(p.id ?? "");
            if (!/^\d+$/.test(id)) return;

            const tr = document.createElement("tr");
            const nominal = p.nominal ?? 0;
            const tanggal = p.tanggalPengeluaran ? p.tanggalPengeluaran : '';
            const keterangan = p.keterangan ?? '';
            const jenis = p.jenis ?? '';
            const kategori = p.kategori ?? '';

            appendTextCell(tr, kategori);
            appendTextCell(tr, jenis);
            const nominalCell = appendTextCell(tr, "", "format-rupiah");
            nominalCell.dataset.value = String(nominal);
            appendTextCell(tr, tanggal);
            appendTextCell(tr, keterangan);

            const actionCell = document.createElement("td");
            const editButton = document.createElement("button");
            editButton.type = "button";
            editButton.className = "btn-edit";
            editButton.textContent = "Edit";
            editButton.dataset.id = id;
            editButton.dataset.jenis = String(jenis);
            editButton.dataset.kategori = String(kategori);
            editButton.dataset.nominal = String(nominal);
            editButton.dataset.tanggal = String(tanggal);
            editButton.dataset.keterangan = String(keterangan);
            actionCell.appendChild(editButton);

            // Hapus dikirim sebagai POST biasa. Override _method tidak aktif di
            // aplikasi ini, sehingga versi lama selalu berakhir 405.
            const deleteForm = document.createElement("form");
            deleteForm.action = `/admin/hapus-pengeluaran/${encodeURIComponent(id)}`;
            deleteForm.method = "post";
            deleteForm.style.display = "inline";

            const csrfInput = document.createElement("input");
            csrfInput.type = "hidden";
            csrfInput.name = "_csrf";
            csrfInput.value = window.csrfToken() || "";
            deleteForm.appendChild(csrfInput);

            const deleteButton = document.createElement("button");
            deleteButton.type = "submit";
            deleteButton.className = "btn-delete";
            deleteButton.textContent = "Hapus";
            deleteForm.appendChild(deleteButton);
            actionCell.appendChild(deleteForm);
            tr.appendChild(actionCell);
            pengeluaranBody.appendChild(tr);
        });

        // format rupiah setelah data di-insert
        applyRupiahFormatting();
    }

    // load awal
    loadPengeluaran();

    // filter event - only for pengeluaran page
    if (filterForm && pengeluaranBody) {
        filterForm.addEventListener("submit", (e) => {
            e.preventDefault();
            const params = {
                jenis: (filterForm.querySelector("[name='jenis']") || {}).value,
                bulan: parseInt((filterForm.querySelector("[name='bulan']") || {}).value) || undefined,
                tahun: parseInt((filterForm.querySelector("[name='tahun']") || {}).value) || undefined
            };
            // remove undefined keys
            Object.keys(params).forEach(k => params[k] === undefined && delete params[k]);
            loadPengeluaran(params);
        });
    }

    //
    // Modal / edit handlers (safety: cek elemen ada sebelum attach)
    //
    const editModal = document.getElementById("editModal");
    const closeModal = document.getElementById("closeModal");
    const editForm = document.getElementById("editForm");
    const editId = document.getElementById("editId");
    const editJenis = document.getElementById("editJenis");
    const editKategori = document.getElementById("editKategori");
    const editNominal = document.getElementById("editNominal");
    const editTanggal = document.getElementById("editTanggal");
    const editKeterangan = document.getElementById("editKeterangan");

    // Delegated click: tangkap klik tombol Edit (works even for dynamically added rows)
    document.addEventListener("click", function (e) {
        const btn = e.target.closest(".btn-edit");
        if (!btn) return;

        // ambil data dari attributes
        const id = btn.dataset.id;
        const jenis = btn.dataset.jenis;
        const kategori = btn.dataset.kategori;
        const nominal = btn.dataset.nominal;

        // pastikan elemen modal/form tersedia
        if (!editModal || !editForm || !editId || !editJenis || !editKategori || !editNominal) {
            console.warn("Modal edit tidak tersedia di DOM.");
            return;
        }

        // isi form modal
        editId.value = id || '';
        editJenis.value = jenis || '';
        editKategori.value = kategori || '';
        editNominal.value = nominal || '';
        if (editTanggal) editTanggal.value = btn.dataset.tanggal || '';
        if (editKeterangan) editKeterangan.value = btn.dataset.keterangan || '';

        // set form action
        editForm.action = `/admin/edit-pengeluaran/${id}`;

        // tampilkan modal dan simpan tombol pemicu untuk pemulihan fokus
        window.SipatikModal.open(editModal, { trigger: btn, initialFocus: editJenis });
    });

    // tutup modal (cek element)
    if (closeModal && editModal) {
        closeModal.addEventListener("click", () => {
            window.SipatikModal.close(editModal);
        });
    }

    //
    // Modal Konfirmasi Hapus
    //
    const confirmDeleteModal = document.getElementById("confirmDeleteModal");
    const okDeleteBtn = document.getElementById("okDeleteBtn");
    const cancelDeleteBtn = document.getElementById("cancelDeleteBtn");
    const confirmDeleteText = document.getElementById("confirmDeleteText");

    let formToSubmit = null; // akan menyimpan form sementara sebelum submit

    // Delegated handler for delete buttons (works for dynamic content)
    document.addEventListener("click", function (e) {
        const btn = e.target.closest(".btn-delete");
        if (!btn) return;

        e.preventDefault();
        const form = btn.closest("form");
        if (!form) return;

        // If confirm modal not present, fallback to direct submit
        if (!confirmDeleteModal || !okDeleteBtn || !cancelDeleteBtn || !confirmDeleteText) {
            form.submit();
            return;
        }

        formToSubmit = form;
        const action = form.getAttribute("action") || "";
        const id = action.split("/").pop();
        confirmDeleteText.textContent = `Yakin ingin menghapus pengeluaran (ID: ${id})?`;
        window.SipatikModal.open(confirmDeleteModal, { trigger: btn, initialFocus: cancelDeleteBtn });
    });

    if (okDeleteBtn) {
        okDeleteBtn.addEventListener("click", function () {
            if (formToSubmit) {
                formToSubmit.submit();
                formToSubmit = null;
            }
            if (confirmDeleteModal) {
                window.SipatikModal.close(confirmDeleteModal);
            }
        });
    }

    if (cancelDeleteBtn) {
        cancelDeleteBtn.addEventListener("click", function () {
            formToSubmit = null;
            if (confirmDeleteModal) {
                window.SipatikModal.close(confirmDeleteModal);
            }
        });
    }

    if (confirmDeleteModal) {
        confirmDeleteModal.addEventListener("click", function (e) {
            if (e.target === confirmDeleteModal) {
                formToSubmit = null;
                window.SipatikModal.close(confirmDeleteModal);
            }
        });

        confirmDeleteModal.addEventListener("sipatik:modal-request-close", function (e) {
            e.preventDefault();
            formToSubmit = null;
            window.SipatikModal.close(confirmDeleteModal);
        });
    }

}); // end DOMContentLoaded
