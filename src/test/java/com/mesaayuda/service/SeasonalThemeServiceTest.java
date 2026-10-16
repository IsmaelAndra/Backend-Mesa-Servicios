package com.mesaayuda.service;

import com.mesaayuda.dto.SeasonalThemeDtos.ConfigRequest;
import com.mesaayuda.dto.SeasonalThemeDtos.TemaRequest;
import com.mesaayuda.entity.SeasonalTheme;
import com.mesaayuda.entity.SeasonalThemeConfig;
import com.mesaayuda.exception.BadRequestException;
import com.mesaayuda.repository.SeasonalThemeConfigRepository;
import com.mesaayuda.repository.SeasonalThemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Optional;

import static com.mesaayuda.IdsPrueba.id;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeasonalThemeServiceTest {

    @Mock private SeasonalThemeRepository repository;
    @Mock private SeasonalThemeConfigRepository configRepository;
    @Mock private AdminService adminService;
    @Mock private UserService userService;

    private SeasonalThemeService service;
    private SeasonalThemeConfig config;
    private SeasonalTheme halloween;
    private SeasonalTheme navidad;
    private SeasonalTheme finDeAnio;

    @BeforeEach
    void setUp() {
        service = new SeasonalThemeService(repository, configRepository, adminService, userService);
        config = new SeasonalThemeConfig();
        when(configRepository.findById(any())).thenReturn(Optional.of(config));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        halloween = tema("HALLOWEEN", 10, 20, 10, 31);
        navidad = tema("NAVIDAD", 12, 1, 12, 26);
        finDeAnio = tema("FIN_ANIO", 12, 27, 1, 2);
        when(repository.findAllByOrderByStartMonthThemeAscStartDayThemeAsc())
                .thenReturn(List.of(halloween, navidad, finDeAnio));
        when(repository.findByCodeTheme("NAVIDAD")).thenReturn(Optional.of(navidad));
    }

    private static SeasonalTheme tema(String code, int m1, int d1, int m2, int d2) {
        SeasonalTheme t = new SeasonalTheme();
        t.setIdTheme(id(code.length()));
        t.setCodeTheme(code);
        t.setNameTheme(code);
        t.setActiveTheme(true);
        t.setStartMonthTheme(m1);
        t.setStartDayTheme(d1);
        t.setEndMonthTheme(m2);
        t.setEndDayTheme(d2);
        return t;
    }

    @Test
    void cadaTemaSaleSoloEnSusFechas() {
        assertEquals("HALLOWEEN", service.temaActual(LocalDate.of(2026, 10, 31)).orElseThrow().getCodeTheme());
        assertEquals("NAVIDAD", service.temaActual(LocalDate.of(2026, 12, 24)).orElseThrow().getCodeTheme());
        assertTrue(service.temaActual(LocalDate.of(2026, 11, 1)).isEmpty());
        assertTrue(service.temaActual(LocalDate.of(2026, 10, 19)).isEmpty());
    }

    @Test
    void unTemaQueCruzaElAnioValeEnDiciembreYEnEnero() {
        assertTrue(SeasonalThemeService.vigente(finDeAnio, MonthDay.of(12, 31)));
        assertTrue(SeasonalThemeService.vigente(finDeAnio, MonthDay.of(1, 2)));
        assertFalse(SeasonalThemeService.vigente(finDeAnio, MonthDay.of(1, 3)));
        assertEquals("FIN_ANIO", service.temaActual(LocalDate.of(2027, 1, 1)).orElseThrow().getCodeTheme());
    }

    @Test
    void elCarnavalSeCalculaCadaAnioDesdeLaPascua() {
        assertEquals(LocalDate.of(2026, 4, 5), SeasonalThemeService.pascua(2026));
        assertEquals(LocalDate.of(2027, 3, 28), SeasonalThemeService.pascua(2027));
        // 2026: sábado 14 a martes 17 de febrero; 2027: sábado 6 a martes 9.
        assertArrayEquals(new LocalDate[] { LocalDate.of(2026, 2, 14), LocalDate.of(2026, 2, 17) },
                SeasonalThemeService.carnaval(2026));
        assertArrayEquals(new LocalDate[] { LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 9) },
                SeasonalThemeService.carnaval(2027));

        SeasonalTheme carnaval = tema("CARNAVAL", 2, 1, 2, 1);
        carnaval.setRuleTheme("CARNAVAL");
        assertTrue(SeasonalThemeService.vigente(carnaval, LocalDate.of(2027, 2, 8)));
        assertFalse(SeasonalThemeService.vigente(carnaval, LocalDate.of(2027, 2, 1)));
        assertFalse(SeasonalThemeService.vigente(carnaval, LocalDate.of(2027, 2, 10)));
    }

    @Test
    void siCoincidenDosGanaElMasCorto() {
        SeasonalTheme quito = tema("FIESTAS_QUITO", 11, 28, 12, 6);
        when(repository.findAllByOrderByStartMonthThemeAscStartDayThemeAsc())
                .thenReturn(List.of(halloween, quito, navidad, finDeAnio));

        assertEquals("FIESTAS_QUITO", service.temaActual(LocalDate.of(2026, 12, 3)).orElseThrow().getCodeTheme());
        assertEquals("NAVIDAD", service.temaActual(LocalDate.of(2026, 12, 7)).orElseThrow().getCodeTheme());
    }

    @Test
    void elSaludoCuentaLosAniosDeLaCooperativa() {
        String s = SeasonalThemeService.saludo("¡{anios} años!");
        int anios = LocalDate.now().getYear() - 1968;
        assertEquals("¡" + anios + " años!", s);
        assertNull(SeasonalThemeService.saludo(null));
    }

    @Test
    void unTemaInactivoNoSale() {
        halloween.setActiveTheme(false);
        assertTrue(service.temaActual(LocalDate.of(2026, 10, 25)).isEmpty());
    }

    @Test
    void elInterruptorGeneralLosApagaTodos() {
        config.setEnabledThemeConfig(false);
        assertTrue(service.temaActual(LocalDate.of(2026, 12, 24)).isEmpty());
    }

    @Test
    void unTemaForzadoSaleFueraDeSusFechas() {
        config.setForcedCodeTheme("NAVIDAD");
        assertEquals("NAVIDAD", service.temaActual(LocalDate.of(2026, 10, 25)).orElseThrow().getCodeTheme());
    }

    @Test
    void validaFechasYTemaForzado() {
        when(repository.findById(any())).thenReturn(Optional.of(halloween));
        assertThrows(BadRequestException.class, () -> service.actualizar(halloween.getIdTheme(),
                new TemaRequest("Halloween", true, 4, 31, 10, 31, null)));
        assertThrows(BadRequestException.class, () -> service.configurar(new ConfigRequest(true, "NO_EXISTE")));
        assertThrows(BadRequestException.class, () -> service.quitarImagen(halloween.getIdTheme(), "../x"));
    }
}
