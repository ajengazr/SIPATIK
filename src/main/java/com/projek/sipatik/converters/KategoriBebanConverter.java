package com.projek.sipatik.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.projek.sipatik.models.KategoriBeban;

@Converter(autoApply = true)
public class KategoriBebanConverter implements AttributeConverter<KategoriBeban, String> {

    @Override
    public String convertToDatabaseColumn(KategoriBeban attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public KategoriBeban convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        // 1) coba langsung cocokkan nama enum modern
        try {
            return KategoriBeban.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            // 2) handle legacy mapping khusus
            String key = dbData.trim().toUpperCase();

            // contoh mapping legacy -> current
            switch (key) {
                case "BENDAHARA_UANG_SAKU":
                case "BENDAHARA UANG SAKU":
                case "BENDAHARA-UANG-SAKU":
                    return KategoriBeban.BENDAHARA;
                // tambahkan case lain bila ada nilai legacy lainnya
            }

            // 3) coba cocokkan berdasarkan label (case-insensitive)
            for (KategoriBeban k : KategoriBeban.values()) {
                if (k.getLabel() != null && k.getLabel().equalsIgnoreCase(dbData)) {
                    return k;
                }
            }

            // 4) jika belum cocok, lempar exception untuk terlihat jelas
            throw new IllegalArgumentException("Unknown KategoriBeban value from DB: " + dbData);
        }
    }
}
