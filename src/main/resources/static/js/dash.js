// dash.js (perbaikan: semua DOM access diletakkan setelah DOMContentLoaded, safety checks)
document.addEventListener('DOMContentLoaded', function () {

    // Elemen UI (ambil setelah DOM siap)
    const sidebarToggleBtns = document.querySelectorAll(".sidebar-toggle");
    const sidebar = document.querySelector(".sidebar");
    const themeToggleBtn = document.querySelector(".theme-toggle");
    const themeIcon = themeToggleBtn ? themeToggleBtn.querySelector(".theme-icon") : null;
    const menuLinks = document.querySelectorAll(".menu-link");

    // Helper: update theme icon (cek keberadaan themeIcon dan sidebar)
    const updateThemeIcon = () => {
        if (!themeIcon || !sidebar) return;
        const isDark = document.body.classList.contains("dark-theme");
        themeIcon.textContent = sidebar.classList.contains("collapsed")
            ? (isDark ? "light_mode" : "dark_mode")
            : "dark_mode";
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
        sidebarToggleBtns.forEach((btn) => {
            btn.addEventListener("click", () => {
                sidebar.classList.toggle("collapsed");
                updateThemeIcon();
            });
        });

        // remove collapsed on wide screens (cek ketersediaan)
        if (window.innerWidth > 768) {
            sidebar.classList.remove("collapsed");
            updateThemeIcon();
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
            const raw = el.getAttribute('data-value') ?? el.textContent;
            el.textContent = formatRupiahNumber(raw);
        });
    }

    window.addEventListener('DOMContentLoaded', () => {
        applyRupiahFormatting();
    });

    // Fungsi toggle show/hide password yang aman (cek element ada)
    const passwordSide = (loginPassId, loginEyeId) => {
        const input = document.getElementById(loginPassId);
        const iconEye = document.getElementById(loginEyeId);

        if (!input || !iconEye) {
            // jika element tidak ada (halaman bukan halaman login), jangan lakukan apa-apa
            return;
        }

        iconEye.addEventListener('click', () => {
            input.type = input.type === 'password' ? 'text' : 'password';
            iconEye.classList.toggle('ri-eye-fill');
            iconEye.classList.toggle('ri-eye-off-fill');
        });
    };

    // Panggil hanya jika elemen ada
    passwordSide('password', 'loginPassword');
    passwordSide('password', 'loginPassword2');

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

    async function loadPengeluaran(params = {}) {
        if (!pengeluaranBody) return;

        const query = new URLSearchParams(params).toString();
        const res = await fetch(`/admin/api/pengeluaran?${query}`);
        if (!res.ok) {
            pengeluaranBody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:#c00;">Gagal memuat data</td></tr>`;
            return;
        }

        const data = await res.json();

        pengeluaranBody.innerHTML = "";

        if (!Array.isArray(data) || data.length === 0) {
            pengeluaranBody.innerHTML =
                `<tr>
                    <td colspan="5" style="text-align:center;">Belum ada data pengeluaran</td>
                </tr>`;
            return;
        }

        data.forEach(p => {
            const tr = document.createElement("tr");
            const nominal = p.nominal ?? 0;
            const tanggal = p.tanggalPengeluaran ? p.tanggalPengeluaran : '';
            tr.innerHTML = `
                <td>${p.kategori ?? ''}</td>
                <td>${p.jenis ?? ''}</td>
                <td class="format-rupiah" data-value="${nominal}"></td>
                <td>${tanggal}</td>
                <td>
                    <button type="button"
                            class="btn-edit"
                            data-id="${p.id}"
                            data-jenis="${p.jenis}"
                            data-kategori="${p.kategori}"
                            data-nominal="${nominal}">
                    Edit
                    </button>
                    <form action="/admin/hapus-pengeluaran/${p.id}" method="post" style="display:inline;">
                        <input type="hidden" name="_method" value="delete"/>
                        <button type="submit" class="btn-delete">Hapus</button>
                    </form>
                </td>
            `;
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

        // set form action
        editForm.action = `/admin/edit-pengeluaran/${id}`;

        // tampilkan modal
        editModal.classList.remove("hidden");
    });

    // tutup modal (cek element)
    if (closeModal && editModal) {
        closeModal.addEventListener("click", () => {
            editModal.classList.add("hidden");
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
        confirmDeleteModal.classList.remove("hidden");
        confirmDeleteModal.setAttribute("aria-hidden", "false");
        cancelDeleteBtn.focus();
    });

    if (okDeleteBtn) {
        okDeleteBtn.addEventListener("click", function () {
            if (formToSubmit) {
                formToSubmit.submit();
                formToSubmit = null;
            }
            if (confirmDeleteModal) {
                confirmDeleteModal.classList.add("hidden");
                confirmDeleteModal.setAttribute("aria-hidden", "true");
            }
        });
    }

    if (cancelDeleteBtn) {
        cancelDeleteBtn.addEventListener("click", function () {
            formToSubmit = null;
            if (confirmDeleteModal) {
                confirmDeleteModal.classList.add("hidden");
                confirmDeleteModal.setAttribute("aria-hidden", "true");
            }
        });
    }

    if (confirmDeleteModal) {
        confirmDeleteModal.addEventListener("click", function (e) {
            if (e.target === confirmDeleteModal) {
                formToSubmit = null;
                confirmDeleteModal.classList.add("hidden");
                confirmDeleteModal.setAttribute("aria-hidden", "true");
            }
        });
    }

    // tutup modal dengan ESC (global)
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") {
            if (confirmDeleteModal && !confirmDeleteModal.classList.contains("hidden")) {
                formToSubmit = null;
                confirmDeleteModal.classList.add("hidden");
                confirmDeleteModal.setAttribute("aria-hidden", "true");
            }
            if (editModal && !editModal.classList.contains("hidden")) {
                editModal.classList.add("hidden");
            }
        }
    });

}); // end DOMContentLoaded
