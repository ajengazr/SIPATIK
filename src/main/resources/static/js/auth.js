function loadNama() {
    const angkatan = document.getElementById("angkatan").value;
    const namaSelect = document.getElementById("nama");
    namaSelect.innerHTML = '<option value="">Loading...</option>';

    fetch('/api/nama-by-angkatan?angkatan=' + angkatan)
        .then(response => response.json())
        .then(data => {
            namaSelect.replaceChildren(new Option('Pilih Nama', ''));
            data.forEach(nama => {
                namaSelect.add(new Option(nama, nama));
            });
        })
        .catch(error => {
            namaSelect.innerHTML = '<option value="">Gagal memuat nama</option>';
            console.error('Error:', error);
        });
}

const passwordSide = (loginPass, loginEye) => {
    const input = document.getElementById(loginPass);
    const iconEye = document.getElementById(loginEye);
    if (!input || !iconEye) return;

    const syncPasswordToggle = () => {
        const isVisible = input.type === 'text';
        iconEye.classList.toggle('ri-eye-fill', isVisible);
        iconEye.classList.toggle('ri-eye-off-fill', !isVisible);
        iconEye.setAttribute('aria-pressed', String(isVisible));
        if (!iconEye.hasAttribute('aria-controls')) {
            iconEye.setAttribute('aria-controls', input.id);
        }
        iconEye.setAttribute('aria-label', isVisible ? 'Sembunyikan password' : 'Tampilkan password');
    };

    syncPasswordToggle();
    iconEye.addEventListener('click', () => {
        input.type = input.type === 'password' ? 'text' : 'password';
        syncPasswordToggle();
    });
};

passwordSide('password', 'loginPassword');

function goToLupaPassword() {
    const nama = document.getElementById('inputNama').value;
    const angkatan = document.getElementById('inputAngkatan').value;
    submitOtpRequest(nama, angkatan);
}
function submitOtpRequest(nama, angkatan) {
    if (!nama || !angkatan) {
        alert('Pilih nama dan angkatan terlebih dahulu.');
        return;
    }

    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/auth/form-otp';
    [['nama', nama], ['angkatan', angkatan]].forEach(([name, value]) => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
    });

    const csrf = document.querySelector('input[name="_csrf"]');
    if (csrf) {
        form.appendChild(csrf.cloneNode(true));
    }
    document.body.appendChild(form);
    form.submit();
}

// ===== FORM OTP =====
function moveToNext(current) {
    const inputs = document.querySelectorAll('.otp-input');
    const index = Array.from(inputs).indexOf(current);
    if (current.value.length === 1 && index < inputs.length - 1) {
        inputs[index + 1].focus();
    }
}

const otpForm = document.querySelector('#otp-form');
if (otpForm) {
    otpForm.addEventListener('submit', function (e) {
        const otpInputs = Array.from(this.querySelectorAll('.otp-input'));
        const otp = otpInputs.map(input => input.value).join('');
        if (!/^\d{6}$/.test(otp)) {
            e.preventDefault(); // hentikan submit
            alert('Harap masukkan 6 digit OTP.');
            return;
        }

        // Set OTP ke input hidden
        document.getElementById('otp-hidden').value = otp;
    });
}

// Sesuaikan tampilan dengan cooldown pengiriman ulang 60 detik di server.
let countdown = 60;
const resendLink = document.getElementById("resendOtpLink");

if (resendLink && otpForm) {
    const timerInterval = setInterval(() => {
        if (countdown <= 0) {
            clearInterval(timerInterval);
            resendLink.disabled = false;
            resendLink.innerText = "Kirim Ulang";
        } else {
            resendLink.innerText = `Kirim Ulang (${countdown} detik)`;
            countdown--;
        }
    }, 1000);
}

// Loading experience for login and account activation pages.
(() => {
    const pageType = document.body?.dataset.authPage;
    const supportedPages = new Set(['login', 'admin-login', 'admin-token', 'register']);
    if (!supportedPages.has(pageType)) return;

    const form = document.querySelector('form[data-auth-form]');
    const loader = document.getElementById('auth-loader');
    const content = document.getElementById('auth-content');
    if (!form || !loader) return;

    const backgroundElements = [
        content,
        document.querySelector('.skip-link'),
        document.querySelector('body.auth-page > .theme-toggle')
    ].filter(Boolean);

    const badge = loader.querySelector('[data-loader-badge]');
    const title = loader.querySelector('#auth-loader-title');
    const description = loader.querySelector('[data-loader-description]');
    const progress = loader.querySelector('[data-loader-progress]');
    const fill = loader.querySelector('[data-loader-fill]');
    const status = loader.querySelector('[data-loader-status]');
    const value = loader.querySelector('[data-loader-value]');
    const submitButtons = Array.from(form.querySelectorAll('button[type="submit"], input[type="submit"]'));
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const submitCopy = {
        login: {
            badge: 'MENGHUBUNGKAN AKUN',
            title: 'Memeriksa akun alumni',
            description: 'Mohon tunggu, kami sedang menyiapkan dashboard Anda.',
            status: 'Memverifikasi data masuk…'
        },
        'admin-login': {
            badge: 'MENGAMANKAN AKSES',
            title: 'Memverifikasi akun admin',
            description: 'Kredensial diperiksa dan token keamanan sedang disiapkan.',
            status: 'Memproses akses admin…'
        },
        'admin-token': {
            badge: 'MEMVERIFIKASI TOKEN',
            title: 'Memvalidasi token keamanan',
            description: 'Token Anda sedang diverifikasi untuk mengakses panel admin.',
            status: 'Memverifikasi token…'
        },
        register: {
            badge: 'MENGAKTIFKAN AKUN',
            title: 'Mengaktifkan akun alumni',
            description: 'Data profil Anda sedang disimpan dengan aman.',
            status: 'Memproses aktivasi akun…'
        }
    };

    const introDescriptions = {
        login: 'Menyiapkan ruang alumni Anda.',
        'admin-login': 'Menyiapkan akses aman untuk pengelola.',
        'admin-token': 'Menyiapkan verifikasi token admin.',
        register: 'Menyiapkan aktivasi akun alumni.'
    };

    let submitting = false;
    let introCompleted = false;
    let focusBeforeLoader;
    let hideTimer;
    let introTimer;
    let introFailsafe;

    const clearLoaderTimers = () => {
        window.clearTimeout(hideTimer);
        window.clearTimeout(introTimer);
        window.clearTimeout(introFailsafe);
    };

    const setProgress = (amount, message) => {
        const normalized = Math.max(0, Math.min(100, amount));
        progress.classList.remove('is-indeterminate');
        progress.setAttribute('aria-valuenow', String(normalized));
        fill.style.width = `${normalized}%`;
        value.textContent = `${normalized}%`;
        if (message) status.textContent = message;
    };

    const setIndeterminate = message => {
        progress.classList.add('is-indeterminate');
        progress.removeAttribute('aria-valuenow');
        fill.style.removeProperty('width');
        value.textContent = '•••';
        status.textContent = message;
    };

    const showLoader = () => {
        window.clearTimeout(hideTimer);
        if (loader.hidden) {
            const activeElement = document.activeElement;
            focusBeforeLoader = activeElement && activeElement !== document.body
                ? activeElement
                : form.querySelector('input:not([type="hidden"]), select, button');
        }
        document.body.classList.add('auth-loading');
        content?.setAttribute('aria-busy', 'true');
        backgroundElements.forEach(element => {
            if ('inert' in element) element.inert = true;
        });
        loader.hidden = false;
        window.requestAnimationFrame(() => {
            loader.classList.add('is-visible');
            loader.focus({ preventScroll: true });
        });
    };

    const hideLoader = (immediate = false) => {
        loader.classList.remove('is-visible');
        const finish = () => {
            if (submitting) return;
            const shouldRestoreFocus = document.activeElement === loader || loader.contains(document.activeElement);
            const focusTarget = focusBeforeLoader;
            loader.hidden = true;
            document.body.classList.remove('auth-loading');
            content?.removeAttribute('aria-busy');
            backgroundElements.forEach(element => {
                if ('inert' in element) element.inert = false;
            });
            if (shouldRestoreFocus && focusTarget?.isConnected) {
                window.requestAnimationFrame(() => focusTarget.focus({ preventScroll: true }));
            }
        };
        if (immediate || reducedMotion) finish();
        else hideTimer = window.setTimeout(finish, 190);
    };

    const resetSubmission = () => {
        submitting = false;
        submitButtons.forEach(button => {
            button.disabled = false;
            button.removeAttribute('aria-disabled');
            button.removeAttribute('aria-busy');
        });
        progress.classList.remove('is-indeterminate');
        hideLoader(true);
    };

    loader.addEventListener('keydown', event => {
        if (event.key === 'Tab') {
            event.preventDefault();
            loader.focus({ preventScroll: true });
        }
    });

    form.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        if (submitting) {
            event.preventDefault();
            return;
        }

        submitting = true;
        clearLoaderTimers();
        const copy = submitCopy[pageType];
        badge.textContent = copy.badge;
        title.textContent = copy.title;
        description.textContent = copy.description;
        setIndeterminate(copy.status);
        showLoader();

        submitButtons.forEach(button => {
            button.setAttribute('aria-disabled', 'true');
            button.setAttribute('aria-busy', 'true');
        });

        // Let the browser serialize the clicked submit button before disabling it.
        window.setTimeout(() => {
            if (event.defaultPrevented) {
                resetSubmission();
                return;
            }
            submitButtons.forEach(button => { button.disabled = true; });
        }, 0);
    });

    const serverError = Array.from(document.querySelectorAll('[role="alert"]'))
        .find(element => element.textContent.trim().length > 0);

    if (serverError) {
        serverError.setAttribute('tabindex', '-1');
        window.requestAnimationFrame(() => serverError.focus({ preventScroll: true }));
    } else {
        const startedAt = performance.now();
        badge.textContent = 'MENYIAPKAN HALAMAN';
        title.textContent = 'SIPATIK';
        description.textContent = introDescriptions[pageType];
        setProgress(50, 'Memuat tampilan…');
        showLoader();

        const fontsReady = document.fonts?.ready ?? Promise.resolve();
        fontsReady.then(() => {
            if (!submitting && !introCompleted && !loader.hidden) {
                setProgress(80, 'Menyiapkan formulir…');
            }
        });

        const completeIntro = () => {
            if (submitting || introCompleted || loader.hidden) return;
            introCompleted = true;
            window.clearTimeout(introFailsafe);
            setProgress(100, 'Siap digunakan');
            const minimumVisible = reducedMotion ? 0 : 450;
            const remaining = Math.max(0, minimumVisible - (performance.now() - startedAt));
            introTimer = window.setTimeout(() => hideLoader(), remaining);
        };

        if (document.readyState === 'complete') completeIntro();
        else window.addEventListener('load', completeIntro, { once: true });
        introFailsafe = window.setTimeout(completeIntro, 1500);
    }

    window.addEventListener('pageshow', event => {
        if (event.persisted || submitting) resetSubmission();
    });

    window.addEventListener('pagehide', clearLoaderTimers, { once: true });
})();
