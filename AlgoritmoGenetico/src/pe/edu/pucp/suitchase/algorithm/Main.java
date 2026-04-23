import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static boolean imprimirItinerarioDetallado(EnvioAlgoritmo envio, ResultadoRuta ruta, Map<String, 
        AeropuertoAlgoritmo> mapaAeropuertos, Map<String, Integer> capacidadesVuelos, Map<String, Integer> ocupacionAlmacenes) {
        System.out.println("-".repeat(93));
        System.out.print("ENVÍO ID: " + envio.getId());

        String contOrigen = mapaAeropuertos.get(envio.getOrigenOaci()).getContinente();
        String contDestino = mapaAeropuertos.get(envio.getDestinoOaci()).getContinente();
        int limiteHoras = contOrigen.equalsIgnoreCase(contDestino) ? 24 : 48;

        if (ruta == null) {
            System.out.println(" | [!!!] ESTADO: COLAPSO CRÍTICO (Sin ruta disponible)");
            System.out.println("   -> Límite exigido: " + limiteHoras + "h (Continentes: " + contOrigen + " -> " + contDestino + ")");
            return true; // Provocó colapso
        }

        long minutosTotales = ChronoUnit.MINUTES.between(envio.getFechaHoraRegistro(), ruta.tiempoLlegadaFinal);
        long horas = minutosTotales / 60;
        long mins = minutosTotales % 60;

        boolean esColapso = horas > limiteHoras;
        String estado = esColapso ? "[!!!] COLAPSO (Fuera de plazo)" : "[OK] A TIEMPO";

        System.out.println(" | ESTADO: " + estado);
        System.out.println("Origen: " + envio.getOrigenOaci() + " (" + contOrigen + ") -> Destino: " + envio.getDestinoOaci() + " (" + contDestino + ")");
        System.out.println("Límite del SLA: " + limiteHoras + " horas | Tiempo Real: " + horas + "h " + mins + "m");
        
        System.out.println("Ruta asignada:");
        for (int i = 0; i < ruta.vuelosUsados.size(); i++) {
            VueloAlgoritmo v = ruta.vuelosUsados.get(i);
            LocalDateTime salida = ruta.fechasVuelo.get(i);
            
            String claveVuelo = v.getOrigenOaci() + "-" + v.getDestinoOaci() + "-" + v.getHoraSalida() + "-" + salida.toLocalDate();
            int disponible = capacidadesVuelos.getOrDefault(claveVuelo, v.getCapacidad());
            int total = v.getCapacidad();
            AeropuertoAlgoritmo aeroOrigen = mapaAeropuertos.get(v.getOrigenOaci());
        
            // Calculamos la ocupación del almacén en el momento de salida
            String claveAlmacen = v.getOrigenOaci() + "-" + ruta.fechasVuelo.get(i).toLocalDate() + "-" + ruta.fechasVuelo.get(i).getHour();
            int ocupado = ocupacionAlmacenes.getOrDefault(claveAlmacen, 0);
            int totalAero = aeroOrigen.getCapacidadAlmacen();

            System.out.printf("   (%d) %s -> %s | Salida: %s | Llegada: %s | Capacidad: %d/%d | Almacén %s: %d/%d\n", 
                (i + 1), v.getOrigenOaci(), v.getDestinoOaci(), 
                salida.toLocalTime(), v.getHoraLlegada(), 
                (total - disponible), total, v.getOrigenOaci(), ocupado, totalAero);
        }

        return esColapso;
    }
    public static void main(String[] args) {
        LectorAeropuertos lectorAeropuertos = new LectorAeropuertos();
        LectorVuelos lectorVuelos = new LectorVuelos();
        LectorEnvios lectorEnvios = new LectorEnvios();

        // Rutas de los archivos
        String rutaAeropuertos = "c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt";
        String rutaVuelos = "planes_vuelo.txt";
        String rutaEnvios = "_envios_preliminar_";

        // 1. Cargar Aeropuertos
        System.out.println("--- 1. CARGANDO AEROPUERTOS ---");
        List<AeropuertoAlgoritmo> aeropuertos = lectorAeropuertos.leerAeropuertos(rutaAeropuertos);
        
        // Crear el mapa de aeropuertos que necesita A* para buscar las coordenadas rápido
        Map<String, AeropuertoAlgoritmo> mapaAeropuertos = new HashMap<>();
        for (AeropuertoAlgoritmo a : aeropuertos) {
            mapaAeropuertos.put(a.getOaci(), a);
        }

        // 2. Cargar Vuelos (Con estandarización GMT)
        System.out.println("\n--- 2. CARGANDO VUELOS ---");
        List<VueloAlgoritmo> vuelos = lectorVuelos.leerVuelos(rutaVuelos, aeropuertos);
        System.out.println("Total de vuelos cargados: " + vuelos.size());

        // 3. Cargar Envíos (Con estandarización GMT y orden cronológico)
        System.out.println("\n--- 3. CARGANDO ENVÍOS (PRELIMINAR) ---");
        lectorEnvios.cargarTodosLosEnvios(rutaEnvios, aeropuertos);

        // 4. Configurar el Algoritmo Genético
        // Le pasamos los vuelos disponibles y el mapa de aeropuertos (para la heurística)
        AlgoritmoGenetico ag = new AlgoritmoGenetico(vuelos, mapaAeropuertos);

        // 5. Iniciar la Simulación del Tiempo
        int saltoDeAlgoritmo = 5; // El sistema planifica cada Sa minutos
        int k = 12; // Constante de proporcionalidad
        int saltoDeConsumoSc = saltoDeAlgoritmo * k; // Sc minutos de ventana temporal
        int tiempoDeAlgoritmo = 10; // El algoritmo genético durará un máximo de Ta segundos por iteración

        // 5. Configurar el rango de la simulación
        // Para definir fecha usar: LocalDateTime.of(Año, Mes, Día, Hora, Minuto)
        // Para NO definir fecha (comportamiento original), usar: null
        
        LocalDateTime fechaInicioSim = LocalDateTime.of(2026, 1, 1, 0, 0); // Ejemplo: Iniciar el 5 de Enero a medianoche
        LocalDateTime fechaFinSim = LocalDateTime.of(2026, 1, 2, 0, 0);  // Ejemplo: Terminar el 7 de Enero a las 23:59

        // Le indicamos al lector desde dónde debe empezar
        lectorEnvios.configurarRangoTemporal(fechaInicioSim);

        System.out.println("\n========== INICIANDO SIMULACIÓN ==========");
        
        int iteracion = 1;
        boolean sistemaColapsado = false;
        long totalMaletasEjecucion = 0;
        long totalEnviosEjecucion = 0;
        LocalDateTime horaBaseActual = (fechaInicioSim != null) ? fechaInicioSim : lectorEnvios.getRelojSimulacion();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");    

        // 6. El Bucle de Simulación
        while (lectorEnvios.hayMasEnvios() && !sistemaColapsado) {
            
            if (fechaFinSim != null && (lectorEnvios.getRelojSimulacion().isAfter(fechaFinSim) 
                || lectorEnvios.getRelojSimulacion().isEqual(fechaFinSim))) break;

            List<EnvioAlgoritmo> enviosTurno = lectorEnvios.obtenerEnviosPorVentanaDeTiempo(saltoDeConsumoSc);
            
            // El algoritmo se ejecuta cuando transcurre el tiempo Sa
            LocalDateTime horaEjecucionSa = horaBaseActual.plusMinutes(saltoDeAlgoritmo);
            // Pero recoge envíos hasta el límite de Sc
            LocalDateTime horaLimiteSc = lectorEnvios.getRelojSimulacion();

            // ENCABEZADO
            System.out.println("\n" + "=".repeat(93));
            System.out.println(" [Iteración " + iteracion + "]");
            System.out.println(" FECHA/HORA PLANIFICACIÓN (Sa): " + horaEjecucionSa.format(fmt));
            System.out.println(" FECHA/HORA CONSUMO HASTA (Sc): " + horaLimiteSc.format(fmt));
            System.out.println("=".repeat(93));
            
            if (!enviosTurno.isEmpty()) {
                
                // --- CONTADORES POR ITERACIÓN ---
                int enviosEnIteracion = enviosTurno.size();
                long maletasEnIteracion = enviosTurno.stream().mapToLong(EnvioAlgoritmo::getCantidadMaletas).sum();
                
                // Acumulamos a los totales globales
                totalEnviosEjecucion += enviosEnIteracion;
                totalMaletasEjecucion += maletasEnIteracion;

                System.out.println(" -> Envíos a planificar en este salto: " + enviosEnIteracion);
                System.out.println(" -> Maletas a planificar en este salto: " + maletasEnIteracion);
                
                // Ejecutar algoritmo pasándole Ta
                List<EnvioAlgoritmo> enviosPlanificados = ag.planificar(enviosTurno, tiempoDeAlgoritmo);
                Map<String, ResultadoRuta> mapaRutas = ag.getMejoresRutasActuales();
                Map<String, Integer> capacidadesActuales = ag.getEstadoCapacidadesFinal();
                Map<String, Integer> ocupacionAlmacenes = ag.getOcupacionAlmacenesFisicos();
                
                System.out.println(" >>> REPORTE DE RUTAS ASIGNADAS <<<");
                for (EnvioAlgoritmo envio : enviosPlanificados) {
                    String claveUnica = envio.getOrigenOaci() + "-" + envio.getId();
                    ResultadoRuta ruta = mapaRutas.get(claveUnica);
                    
                    boolean huboColapso = imprimirItinerarioDetallado(envio, ruta, mapaAeropuertos, capacidadesActuales, ocupacionAlmacenes);
                    if (huboColapso) sistemaColapsado = true;
                }
                
                if (sistemaColapsado) {
                    System.out.println("\n========================================================");
                    System.out.println("  ALERTA CRÍTICA: EL SISTEMA HA ENTRADO EN COLAPSO  ");
                    System.out.println("========================================================");
                    break;
                }

            } else {
                System.out.println(" -> No hay nuevos envíos en este lapso de tiempo.");
            }
            
            horaBaseActual = horaBaseActual.plusMinutes(saltoDeAlgoritmo);
            iteracion++;
        }

        // --- RESUMEN FINAL DE LA EJECUCIÓN ---
        System.out.println("\n==========================================================");
        System.out.println("                RESUMEN FINAL DE SIMULACIÓN               ");
        System.out.println("==========================================================");
        if (!sistemaColapsado) {
            System.out.println(" ESTADO: Finalizada con éxito (Sin retrasos)");
        } else {
            System.out.println(" ESTADO: Finalizada por COLAPSO");
        }
        System.out.println("----------------------------------------------------------");
        System.out.println(" TOTAL ENVÍOS PROCESADOS:  " + totalEnviosEjecucion);
        System.out.println(" TOTAL MALETAS PROCESADAS: " + totalMaletasEjecucion);
        System.out.println("==========================================================");
    }
}