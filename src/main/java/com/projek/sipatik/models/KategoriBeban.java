package com.projek.sipatik.models;

public enum KategoriBeban {
    KETUA_KEAMANAN("Beban Ketua & Keamanan"),
    BENDAHARA("Beban Bendahara (uang Saku)"),
    SEKRETARIS("Beban Sekretaris"),
    PPMB_PUB("Beban PPMB PUB"),
    DIVISI_PENDIDIKAN("Beban Divisi Pendidikan"),
    DIVISI_KEASRAMAAN("Beban Divisi Keasramaan"),
    DIVISI_KESEJAHTERAAN("Beban Divisi Kesejahteraan"),
    DIVISI_KESEHATAN("Beban Divisi Kesehatan"),
    DIVISI_KEBERSIHAN("Beban Divisi Kebersihan"),
    DIVISI_MAGANG("Beban Divisi Magang"),
    DIVISI_KEROHANIAN("Beban Divisi Kerohanian"),
    PENDIDIKAN_MANAJEMEN("Beban Pendidikan & Manajemen"),
    BANK("Beban Bank"),
    KANTOR_PUB("Beban Kantor PUB"),
    LAIN_LAIN("Beban Lain-Lain");

    private final String label;
    KategoriBeban(String label){ this.label = label; }
    public String getLabel(){ return label; }
}
