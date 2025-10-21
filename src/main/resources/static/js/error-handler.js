// Global Error Handler untuk API calls
class ErrorHandler {
    constructor() {
        this.setupGlobalErrorHandling();
    }

    setupGlobalErrorHandling() {
        // Override fetch untuk menangkap error secara global
        const originalFetch = window.fetch;
        window.fetch = async (...args) => {
            try {
                const response = await originalFetch(...args);
                
                // Jika response tidak ok, handle error
                if (!response.ok) {
                    await this.handleHttpError(response, args[0]);
                }
                
                return response;
            } catch (error) {
                this.handleNetworkError(error);
                throw error;
            }
        };
    }

    async handleHttpError(response, request) {
        let errorData;
        try {
            errorData = await response.json();
        } catch (e) {
            errorData = {
                message: 'Terjadi kesalahan pada server',
                status: response.status
            };
        }

        const status = response.status;
        const message = errorData.message || this.getDefaultErrorMessage(status);

        switch (status) {
            case 400:
                this.showErrorNotification(message, 'warning');
                break;
            case 401:
                this.showErrorNotification('Sesi Anda telah berakhir, silakan login kembali', 'error');
                setTimeout(() => {
                    window.location.href = '/auth/login';
                }, 2000);
                break;
            case 403:
                this.showErrorNotification('Anda tidak memiliki akses untuk melakukan aksi ini', 'error');
                break;
            case 404:
                this.showErrorNotification('Data tidak ditemukan', 'error');
                break;
            case 500:
                this.showErrorNotification('Terjadi kesalahan pada server, silakan coba lagi', 'error');
                break;
            default:
                this.showErrorNotification(message, 'error');
        }
    }

    handleNetworkError(error) {
        console.error('Network error:', error);
        this.showErrorNotification('Koneksi internet bermasalah, silakan coba lagi', 'error');
    }

    getDefaultErrorMessage(status) {
        const messages = {
            400: 'Permintaan tidak valid',
            401: 'Anda perlu login untuk mengakses halaman ini',
            403: 'Anda tidak memiliki akses ke halaman ini',
            404: 'Halaman tidak ditemukan',
            500: 'Terjadi kesalahan pada server'
        };
        return messages[status] || 'Terjadi kesalahan yang tidak diketahui';
    }

    showErrorNotification(message, type = 'error') {
        // Hapus notifikasi sebelumnya jika ada
        const existingNotification = document.querySelector('.error-notification');
        if (existingNotification) {
            existingNotification.remove();
        }

        // Buat elemen notifikasi
        const notification = document.createElement('div');
        notification.className = `error-notification alert alert-${type === 'error' ? 'danger' : 'warning'} alert-dismissible fade show`;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            z-index: 9999;
            min-width: 300px;
            max-width: 500px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        `;

        notification.innerHTML = `
            <i class="fas fa-${type === 'error' ? 'exclamation-triangle' : 'exclamation-circle'} me-2"></i>
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;

        document.body.appendChild(notification);

        // Auto remove setelah 5 detik
        setTimeout(() => {
            if (notification.parentNode) {
                notification.remove();
            }
        }, 5000);
    }

    // Method untuk validasi form
    validateForm(formElement) {
        const inputs = formElement.querySelectorAll('input[required], select[required], textarea[required]');
        let isValid = true;
        const errors = {};

        inputs.forEach(input => {
            const value = input.value.trim();
            const fieldName = input.name || input.id;

            if (!value) {
                errors[fieldName] = `${this.getFieldLabel(fieldName)} tidak boleh kosong`;
                isValid = false;
                this.highlightError(input);
            } else {
                this.clearError(input);
            }
        });

        if (!isValid) {
            this.showValidationErrors(errors);
        }

        return isValid;
    }

    getFieldLabel(fieldName) {
        const labels = {
            'email': 'Email',
            'password': 'Password',
            'nama': 'Nama',
            'angkatan': 'Angkatan',
            'nomorHp': 'Nomor HP',
            'jenjang': 'Jenjang',
            'jumlah': 'Jumlah',
            'keterangan': 'Keterangan'
        };
        return labels[fieldName] || fieldName;
    }

    highlightError(input) {
        input.classList.add('is-invalid');
        input.style.borderColor = '#dc3545';
    }

    clearError(input) {
        input.classList.remove('is-invalid');
        input.style.borderColor = '';
    }

    showValidationErrors(errors) {
        const errorMessages = Object.values(errors).join('<br>');
        this.showErrorNotification(errorMessages, 'warning');
    }
}

// Inisialisasi error handler
const errorHandler = new ErrorHandler();

// Export untuk penggunaan global
window.ErrorHandler = errorHandler;



