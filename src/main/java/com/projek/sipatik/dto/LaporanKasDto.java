package com.projek.sipatik.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LaporanKasDto {
  private List<Item> pemasukanList;
  private List<Item> pengeluaranList;
  private List<KasItem> kasAwalList;
  private List<KasItem> kasAkhirList;

  private BigDecimal totalPemasukan;
  private BigDecimal totalPengeluaran;
  private BigDecimal kenaikanKas;
  private BigDecimal totalKasAwal;
  private BigDecimal totalKasAkhir;

  private String bendahara;
  private String ketua;
  private String pembina;

  @Data
  @Builder
  public static class Item {
    private String keterangan;
    private BigDecimal jumlah;
  }

  @Data
  @Builder
  public static class KasItem {
    private String namaBank;
    private BigDecimal saldo;
  }
}