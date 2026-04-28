import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Simulador unificado para TASF.B2B.
 *
 * Soporta dos algoritmos metaheurísticos:
 *   - AG  : Algoritmo Genético
 *   - ACS : Ant Colony System
 *
 * Parámetros de simulación (igual que la implementación AG original):
 *   Sa  = Salto del algoritmo (minutos simulados entre ejecuciones)
 *   k   = Constante de proporcionalidad
 *   Sc  = k * Sa = ventana de consumo de datos (minutos)
 *   Ta  = Tiempo máximo del algoritmo por bloque
 *         AG  → Ta en SEGUNDOS (int)
 *         ACS → Ta en MILISEGUNDOS (long = Ta * 1000)
 *
 * Rango temporal:
 *   fechaInicioSim / fechaFinSim → LocalDateTime o null
 *   (null = comportamiento original: desde el primer envío cargado)
 */
public class Simulador {

    /** Tipos de algoritmo disponibles */
    public enum Algoritmo { AG, ACS }

    // ===== PARÁMETROS COMPARTIDOS =====
    private final int           saltoDeAlgoritmoSa;   // minutos
    private final int           k;
    private final int           saltoDeConsumoSc;     // minutos = k * Sa
    private final int           tiempoDeAlgoritmoTa;  // segundos (AG) / base para ACS
    private final LocalDateTime fechaInicioSim;
    private final LocalDateTime fechaFinSim;
    private final Algoritmo     algoritmo;

    // ===== FORMATO DE FECHAS =====
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // =======================================================================
    //  Constructor
    // =======================================================================

    /**
     * @param saltoDeAlgoritmoSa  Minutos simulados entre ejecuciones (Sa)
     * @param k                   Constante de proporcionalidad
     * @param tiempoDeAlgoritmoTa Tiempo máximo por bloque en SEGUNDOS
     * @param fechaInicioSim      Inicio de la simulación (null = auto)
     * @param fechaFinSim         Fin de la simulación (null = hasta agotar envíos)
     * @param algoritmo           AG o ACS
     */
    public Simulador(int saltoDeAlgoritmoSa, int k, int tiempoDeAlgoritmoTa,
                     LocalDateTime fechaInicioSim, LocalDateTime fechaFinSim,
                     Algoritmo algoritmo) {
        this.saltoDeAlgoritmoSa  = saltoDeAlgoritmoSa;
        this.k                   = k;
        this.saltoDeConsumoSc    = k * saltoDeAlgoritmoSa;
        this.tiempoDeAlgoritmoTa = tiempoDeAlgoritmoTa;
        this.fechaInicioSim      = fechaInicioSim;
        this.fechaFinSim         = fechaFinSim;
        this.algoritmo           = algoritmo;
    }

    // =======================================================================
    //  Punto de entrada
    // =======================================================================

    /**
     * Ejecuta la simulación completa con el algoritmo configurado.
     *
     * @param rutaAeropuertos  Ruta del archivo de aeropuertos
     * @param rutaVuelos       Ruta del archivo de vuelos
     * @param carpetaEnvios    Ruta de la carpeta con archivos de envíos
     * @param archivoReporte   Nombre del archivo de reporte de salida
     */
    public void ejecutar(String rutaAeropuertos, String rutaVuelos,
                         String carpetaEnvios,   String archivoReporte) {

        // --- Inicializar Logger ---
        String logFile = "log_" + algoritmo.name().toLowerCase() + ".txt";
        Logger.init(logFile);
        Logger.separador();
        Logger.info("SIMULACIÓN " + algoritmo + " INICIADA");
        Logger.info("Parámetros: Sa=" + saltoDeAlgoritmoSa + "min | k=" + k
                + " | Sc=" + saltoDeConsumoSc + "min | Ta=" + tiempoDeAlgoritmoTa + "s");

        // --- 1. Cargar aeropuertos ---
        imprimirSeccion("1. CARGANDO AEROPUERTOS");
        LectorAeropuertos lectorAero = new LectorAeropuertos();
        List<AeropuertoAlgoritmo> aeropuertos = lectorAero.leerAeropuertos(rutaAeropuertos);

        Map<String, AeropuertoAlgoritmo> mapaAeropuertos = new HashMap<>();
        for (AeropuertoAlgoritmo a : aeropuertos) mapaAeropuertos.put(a.getOaci(), a);
        Logger.info("Aeropuertos cargados: " + aeropuertos.size());

        // --- 2. Cargar vuelos ---
        imprimirSeccion("2. CARGANDO VUELOS");
        LectorVuelos lectorVuelos = new LectorVuelos();
        List<VueloAlgoritmo> vuelos = lectorVuelos.leerVuelos(rutaVuelos, aeropuertos);
        System.out.println("Total de vuelos cargados: " + vuelos.size());
        Logger.info("Vuelos cargados: " + vuelos.size());

        // --- 3. Cargar envíos ---
        imprimirSeccion("3. CARGANDO ENVÍOS");
        LectorEnvios lectorEnvios = new LectorEnvios();
        lectorEnvios.cargarTodosLosEnvios(carpetaEnvios, aeropuertos);
        lectorEnvios.configurarRangoTemporal(fechaInicioSim);

        // --- 4. Construir PlanificationProblemInput maestro ---
        PlanificationProblemInput inputMaestro = new PlanificationProblemInput();
        for (AeropuertoAlgoritmo a : aeropuertos) inputMaestro.agregarAeropuerto(a);
        for (VueloAlgoritmo v : vuelos) inputMaestro.agregarVuelo(v);

        // --- 5. Bucle de simulación ---
        imprimirSeccion("INICIANDO SIMULACIÓN - ALGORITMO: " + algoritmo);
        System.out.printf("  Sa=%dmin | k=%d | Sc=%dmin | Ta=%ds%n",
                saltoDeAlgoritmoSa, k, saltoDeConsumoSc, tiempoDeAlgoritmoTa);

        int iteracion = 1;
        boolean sistemaColapsado = false;
        long totalMaletas = 0;
        long totalEnvios  = 0;
        LocalDateTime horaBase = (fechaInicioSim != null)
                ? fechaInicioSim
                : lectorEnvios.getRelojSimulacion();

        // Acumular todas las soluciones para el reporte final
        List<PlanificationSolutionOutput> todasLasSoluciones = new ArrayList<>();

        long tiempoSimulacionInicio = System.currentTimeMillis();

        while (lectorEnvios.hayMasEnvios() && !sistemaColapsado) {

            // Condición de corte por fecha final
            if (fechaFinSim != null) {
                LocalDateTime reloj = lectorEnvios.getRelojSimulacion();
                if (reloj != null && !reloj.isBefore(fechaFinSim)) break;
            }

            List<EnvioAlgoritmo> enviosTurno =
                    lectorEnvios.obtenerEnviosPorVentanaDeTiempo(saltoDeConsumoSc);

            LocalDateTime horaEjecucionSa  = horaBase.plusMinutes(saltoDeAlgoritmoSa);
            LocalDateTime horaLimiteSc     = lectorEnvios.getRelojSimulacion();

            // Encabezado de iteración (solo en terminal)
            System.out.println("\n" + "=".repeat(90));
            System.out.printf(" [Iteración %d | %s]%n", iteracion, algoritmo);
            System.out.println(" FECHA/HORA PLANIFICACIÓN (Sa): " + horaEjecucionSa.format(FMT));
            System.out.println(" FECHA/HORA CONSUMO HASTA (Sc): " + horaLimiteSc.format(FMT));
            System.out.println("=".repeat(90));

            Logger.separador();
            Logger.info("[Iteración " + iteracion + "] Sa=" + horaEjecucionSa.format(FMT)
                    + " | Sc=" + horaLimiteSc.format(FMT));

            if (!enviosTurno.isEmpty()) {

                int    enviosIt  = enviosTurno.size();
                long   maletasIt = enviosTurno.stream().mapToLong(EnvioAlgoritmo::getCantidadMaletas).sum();
                totalEnvios  += enviosIt;
                totalMaletas += maletasIt;

                System.out.println(" -> Envíos a planificar: " + enviosIt
                        + " | Maletas: " + maletasIt);
                Logger.info("Envíos bloque: " + enviosIt + " | Maletas: " + maletasIt);

                // Crear sub-input compartiendo vuelos y aeropuertos
                PlanificationProblemInput subInput = inputMaestro.crearSubInput(enviosTurno);

                // ---- Ejecutar el algoritmo seleccionado ----
                long t0 = System.currentTimeMillis();
                PlanificationSolutionOutput solucion = ejecutarAlgoritmo(subInput);
                long duracion = System.currentTimeMillis() - t0;

                Logger.info("Algoritmo ejecutado en " + duracion + "ms | Métrica: "
                        + String.format("%.4f", solucion.getMetricaUnificada()));

                todasLasSoluciones.add(solucion);

                // Imprimir reporte de rutas (en Logger, no en terminal)
                boolean colapso = reportarSolucion(solucion, mapaAeropuertos, iteracion);
                if (colapso) {
                    sistemaColapsado = true;
                    System.out.println("\n  *** COLAPSO DETECTADO EN ITERACIÓN " + iteracion + " ***");
                    Logger.info("¡COLAPSO! Sistema fuera de SLA en iteración " + iteracion);
                }

                System.out.printf(" -> Tiempo ejecución algoritmo: %dms | Métrica calidad: %.4f%n",
                        duracion, solucion.getMetricaUnificada());

            } else {
                System.out.println(" -> No hay nuevos envíos en este lapso.");
                Logger.info("Sin envíos en este lapso.");
            }

            horaBase = horaBase.plusMinutes(saltoDeAlgoritmoSa);
            iteracion++;
        }

        long tiempoTotalMs = System.currentTimeMillis() - tiempoSimulacionInicio;

        // --- 6. Resumen final ---
        imprimirResumenFinal(sistemaColapsado, totalEnvios, totalMaletas,
                iteracion - 1, tiempoTotalMs);
        generarReporteDetallado(todasLasSoluciones, mapaAeropuertos,
                archivoReporte, totalEnvios, totalMaletas, tiempoTotalMs);

        Logger.separador();
        Logger.info("SIMULACIÓN FINALIZADA | Total envíos=" + totalEnvios
                + " | Maletas=" + totalMaletas + " | Tiempo=" + (tiempoTotalMs / 1000) + "s");
        Logger.close();
    }

    // =======================================================================
    //  Ejecución del algoritmo
    // =======================================================================

    private PlanificationSolutionOutput ejecutarAlgoritmo(PlanificationProblemInput subInput) {
        switch (algoritmo) {
            case AG:
                return AGAdapter.planificar(subInput, tiempoDeAlgoritmoTa);
            case ACS:
                long tiempoMs = (long) tiempoDeAlgoritmoTa * 1000L;
                return ACSAdapter.planificar(subInput, tiempoMs);
            default:
                throw new IllegalStateException("Algoritmo desconocido: " + algoritmo);
        }
    }

    // =======================================================================
    //  Reporte de solución (al Logger)
    // =======================================================================

    /**
     * Escribe el detalle de cada envío al Logger.
     * @return true si se detectó algún colapso (SLA excedido o sin ruta)
     */
    private boolean reportarSolucion(PlanificationSolutionOutput solucion,
                                     Map<String, AeropuertoAlgoritmo> mapaAeropuertos,
                                     int iteracion) {
        boolean hayColapso = false;

        Logger.info(">>> REPORTE DE RUTAS ASIGNADAS - Iteración " + iteracion + " <<<");

        for (EnvioAlgoritmo envio : solucion.getEnviosPlanificados()) {
            ResultadoRuta ruta = solucion.getRuta(envio);

            AeropuertoAlgoritmo aOrig = mapaAeropuertos.get(envio.getOrigenOaci());
            AeropuertoAlgoritmo aDest = mapaAeropuertos.get(envio.getDestinoOaci());

            String contOrigen  = aOrig != null ? aOrig.getContinente() : "?";
            String contDestino = aDest != null ? aDest.getContinente() : "?";
            int limiteHoras    = contOrigen.equalsIgnoreCase(contDestino) ? 24 : 48;

            StringBuilder sb = new StringBuilder();
            sb.append("-".repeat(80)).append("\n");
            sb.append("ENVÍO: ").append(envio.getId())
              .append(" | ").append(envio.getOrigenOaci())
              .append(" (").append(contOrigen).append(")")
              .append(" -> ").append(envio.getDestinoOaci())
              .append(" (").append(contDestino).append(")")
              .append(" | ").append(envio.getCantidadMaletas()).append(" maletas\n");
            sb.append("Registro: ").append(envio.getFechaHoraRegistro().format(FMT))
              .append(" | SLA: ").append(limiteHoras).append("h\n");

            if (ruta == null) {
                sb.append("ESTADO: [!!!] COLAPSO - Sin ruta disponible\n");
                hayColapso = true;
            } else {
                long minutos = ChronoUnit.MINUTES.between(
                        envio.getFechaHoraRegistro(), ruta.tiempoLlegadaFinal);
                long horas   = minutos / 60;
                long mins    = minutos % 60;

                // Verificar si la ruta no alcanzó el destino final (ruta parcial)
                String destinoAlcanzado = ruta.vuelosUsados.isEmpty()
                        ? envio.getOrigenOaci()
                        : ruta.vuelosUsados.get(ruta.vuelosUsados.size() - 1).getDestinoOaci();
                boolean destinoIncompleto = !destinoAlcanzado.equals(envio.getDestinoOaci());

                boolean fuera = horas > limiteHoras || destinoIncompleto;
                if (fuera) hayColapso = true;

                String estadoDetalle = destinoIncompleto
                        ? "[!!!] COLAPSO (Ruta incompleta, detenido en " + destinoAlcanzado + ")"
                        : fuera ? "[!!!] COLAPSO (Fuera de SLA)" : "[OK] A TIEMPO";

                sb.append("ESTADO: ").append(estadoDetalle).append("\n");
                sb.append("Tiempo real: ").append(horas).append("h ").append(mins).append("m")
                  .append(" | Llegada: ").append(ruta.tiempoLlegadaFinal.format(FMT)).append("\n");
                sb.append("Ruta (").append(ruta.vuelosUsados.size()).append(" vuelos):\n");

                for (int i = 0; i < ruta.vuelosUsados.size(); i++) {
                    VueloAlgoritmo v      = ruta.vuelosUsados.get(i);
                    LocalDateTime  salida = ruta.fechasVuelo.get(i);

                    // Snapshots guardados en el momento de la asignación
                    int capUsada   = (ruta.capacidadUsadaVuelo != null && i < ruta.capacidadUsadaVuelo.size())
                            ? ruta.capacidadUsadaVuelo.get(i) : 0;
                    int usoAlmacen = (ruta.ocupacionAlmacenOrigen != null && i < ruta.ocupacionAlmacenOrigen.size())
                            ? ruta.ocupacionAlmacenOrigen.get(i) : 0;

                    AeropuertoAlgoritmo aeroOrigen = mapaAeropuertos.get(v.getOrigenOaci());
                    int capAlmacen = aeroOrigen != null ? aeroOrigen.getCapacidadAlmacen() : 0;

                    sb.append(String.format(
                            "  (%d) %s -> %s | Salida: %s | Llegada: %s | Vuelo: %d/%d | Almacén %s: %d/%d%n",
                            i + 1,
                            v.getOrigenOaci(), v.getDestinoOaci(),
                            salida.toLocalTime(), v.getHoraLlegada(),
                            capUsada, v.getCapacidad(),
                            v.getOrigenOaci(), usoAlmacen, capAlmacen));
                }
            }

            Logger.info(sb.toString());
        }

        return hayColapso;
    }

    // =======================================================================
    //  Reporte detallado en archivo
    // =======================================================================

    private void generarReporteDetallado(
            List<PlanificationSolutionOutput> soluciones,
            Map<String, AeropuertoAlgoritmo> mapaAeropuertos,
            String archivoReporte,
            long totalEnvios, long totalMaletas, long tiempoTotalMs) {

        try (PrintWriter pw = new PrintWriter(new FileWriter(archivoReporte, false))) {

            pw.println("=".repeat(90));
            pw.println("  REPORTE FINAL DE SIMULACIÓN - TASF.B2B");
            pw.println("  Algoritmo: " + algoritmo);
            pw.println("=".repeat(90));
            pw.printf("  Parámetros: Sa=%dmin | k=%d | Sc=%dmin | Ta=%ds%n",
                    saltoDeAlgoritmoSa, k, saltoDeConsumoSc, tiempoDeAlgoritmoTa);
            if (fechaInicioSim != null)
                pw.println("  Inicio simulación : " + fechaInicioSim.format(FMT));
            if (fechaFinSim != null)
                pw.println("  Fin simulación    : " + fechaFinSim.format(FMT));
            pw.println("=".repeat(90));
            pw.println();

            int totalBloques       = soluciones.size();
            int enviosConRuta      = 0;
            int enviosSinRuta      = 0;
            int enviosATiempo      = 0;
            int enviosColapso      = 0;

            for (int b = 0; b < soluciones.size(); b++) {
                PlanificationSolutionOutput sol = soluciones.get(b);
                pw.println("-".repeat(90));
                pw.printf("BLOQUE %d | Envíos: %d | Maletas: %d | Métrica: %.4f%n",
                        b + 1, sol.totalEnvios(), sol.totalMaletas(), sol.getMetricaUnificada());
                pw.println("-".repeat(90));

                for (EnvioAlgoritmo envio : sol.getEnviosPlanificados()) {
                    ResultadoRuta ruta = sol.getRuta(envio);

                    AeropuertoAlgoritmo aOrig = mapaAeropuertos.get(envio.getOrigenOaci());
                    AeropuertoAlgoritmo aDest = mapaAeropuertos.get(envio.getDestinoOaci());
                    String cO = aOrig != null ? aOrig.getContinente() : "?";
                    String cD = aDest != null ? aDest.getContinente() : "?";
                    int lim   = cO.equalsIgnoreCase(cD) ? 24 : 48;

                    pw.printf("  ENVÍO %-15s | %s (%s) -> %s (%s) | %d maletas%n",
                            envio.getId(),
                            envio.getOrigenOaci(), cO,
                            envio.getDestinoOaci(), cD,
                            envio.getCantidadMaletas());

                    if (ruta == null) {
                        pw.println("    ESTADO : [!!!] SIN RUTA (COLAPSO)");
                        enviosSinRuta++;
                        enviosColapso++;
                    } else {
                        long minutos = ChronoUnit.MINUTES.between(
                                envio.getFechaHoraRegistro(), ruta.tiempoLlegadaFinal);
                        long h = minutos / 60, m = minutos % 60;
                        String destAlcanzado = ruta.vuelosUsados.isEmpty()
                                ? envio.getOrigenOaci()
                                : ruta.vuelosUsados.get(ruta.vuelosUsados.size() - 1).getDestinoOaci();
                        boolean incompleto = !destAlcanzado.equals(envio.getDestinoOaci());
                        boolean fuera = h > lim || incompleto;
                        enviosConRuta++;
                        if (fuera) enviosColapso++; else enviosATiempo++;

                        String estadoStr = incompleto
                                ? "[!!!] RUTA INCOMPLETA (parada en " + destAlcanzado + ")"
                                : fuera ? "[!!!] COLAPSO (Fuera de SLA)" : "[OK] A TIEMPO";
                        pw.printf("    ESTADO : %s%n", estadoStr);
                        pw.printf("    Tiempo : %dh %dm | SLA: %dh | Llegada: %s%n",
                                h, m, lim, ruta.tiempoLlegadaFinal.format(FMT));
                        pw.println("    Ruta:");
                        for (int i = 0; i < ruta.vuelosUsados.size(); i++) {
                            VueloAlgoritmo v     = ruta.vuelosUsados.get(i);
                            LocalDateTime  salida = ruta.fechasVuelo.get(i);

                            // Snapshots guardados en el momento de la asignación
                            int capUsada   = (ruta.capacidadUsadaVuelo != null && i < ruta.capacidadUsadaVuelo.size())
                                    ? ruta.capacidadUsadaVuelo.get(i) : 0;
                            int usoAlmacen = (ruta.ocupacionAlmacenOrigen != null && i < ruta.ocupacionAlmacenOrigen.size())
                                    ? ruta.ocupacionAlmacenOrigen.get(i) : 0;

                            AeropuertoAlgoritmo aero = mapaAeropuertos.get(v.getOrigenOaci());
                            int capAlmacen = aero != null ? aero.getCapacidadAlmacen() : 0;

                            pw.printf("      (%d) %s -> %s | Salida: %s | Llegada: %s | Vuelo: %d/%d | Almacén %s: %d/%d%n",
                                    i + 1,
                                    v.getOrigenOaci(), v.getDestinoOaci(),
                                    salida.toLocalTime(), v.getHoraLlegada(),
                                    capUsada, v.getCapacidad(),
                                    v.getOrigenOaci(), usoAlmacen, capAlmacen);
                        }
                    }
                    pw.println();
                }
            }

            // --- Resumen estadístico ---
            pw.println("=".repeat(90));
            pw.println("  RESUMEN ESTADÍSTICO");
            pw.println("=".repeat(90));
            pw.printf("  Total bloques procesados : %d%n",    totalBloques);
            pw.printf("  Total envíos procesados  : %d%n",    totalEnvios);
            pw.printf("  Total maletas procesadas : %d%n",    totalMaletas);
            pw.printf("  Envíos con ruta          : %d%n",    enviosConRuta);
            pw.printf("  Envíos sin ruta          : %d%n",    enviosSinRuta);
            pw.printf("  Envíos a tiempo (SLA OK) : %d%n",    enviosATiempo);
            pw.printf("  Envíos en colapso        : %d%n",    enviosColapso);
            if (totalEnvios > 0)
                pw.printf("  Tasa de éxito            : %.1f%%%n",
                        100.0 * enviosATiempo / totalEnvios);
            pw.printf("  Tiempo total simulación  : %ds%n",   tiempoTotalMs / 1000);
            pw.println("=".repeat(90));

            System.out.println("\n>> Reporte generado en: " + archivoReporte);

        } catch (IOException e) {
            System.err.println("[Simulador] Error escribiendo reporte: " + e.getMessage());
        }
    }

    // =======================================================================
    //  Helpers de presentación en terminal
    // =======================================================================

    private void imprimirSeccion(String titulo) {
        System.out.println("\n--- " + titulo + " ---");
        Logger.info("--- " + titulo + " ---");
    }

    private void imprimirResumenFinal(boolean colapsado, long envios, long maletas,
                                      int iteraciones, long tiempoMs) {
        System.out.println("\n" + "=".repeat(90));
        System.out.println("  RESUMEN FINAL - ALGORITMO: " + algoritmo);
        System.out.println("=".repeat(90));
        System.out.println("  ESTADO         : " + (colapsado ? "COLAPSADO" : "FINALIZADO SIN COLAPSO"));
        System.out.println("  Total iterac.  : " + iteraciones);
        System.out.println("  Total envíos   : " + envios);
        System.out.println("  Total maletas  : " + maletas);
        System.out.printf( "  Tiempo total   : %ds%n", tiempoMs / 1000);
        System.out.println("=".repeat(90));
    }
}
