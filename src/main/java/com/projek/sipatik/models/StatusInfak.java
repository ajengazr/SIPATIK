package com.projek.sipatik.models;

/**
 * Status siklus hidup satu setoran infak.
 *
 * Sebelumnya siklus ini diwakili oleh satu boolean {@code dikonfirmasi}, sehingga
 * setoran yang ditolak admin tidak bisa dibedakan dari setoran yang belum diproses.
 * Enum ini memisahkan ketiga kondisi bisnis tersebut secara eksplisit.
 */
public enum StatusInfak {
    MENUNGGU("Menunggu"),
    DIKONFIRMASI("Dikonfirmasi"),
    DITOLAK("Ditolak");

    private final String label;

    StatusInfak(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
