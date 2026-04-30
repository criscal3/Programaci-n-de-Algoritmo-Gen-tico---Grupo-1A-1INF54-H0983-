import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Adaptador ACS → estructuras canónicas del AG.
 *
 * Semántica de mapas (idéntica a AlgoritmoGenetico):
 *   estadoCapacidadesVuelos  → capacidad actual
 *   ocupacionAlmacenes       → maletas acumuladas por hora (clave: OACI-fecha-hora)
 */
public class ACSAdapter {

    public static PlanificationSolutionOutput planificar(PlanificationProblemInput input, long tiempoMs) {

        PlanificationSolutionOutput output = new PlanificationSolutionOutput("ACS");
        if (input.getEnvios().isEmpty()) {
            output.setMetricaUnificada(0.0);
            return output;
        }

        Map<String, Integer> capVuelos = new HashMap<>(input.getOcupacionGlobalVuelos());
        Map<String, Integer> capAlmacenes = new HashMap<>(input.getOcupacionGlobalAlmacenes());

        // --- 1. Aeropuerto ACS interno ---
        Map<String, Aeropuerto> mapaAeropuertosACS = new HashMap<>();
        for (AeropuertoAlgoritmo aa : input.getMapaAeropuertos().values()) {
            mapaAeropuertosACS.put(aa.getOaci(),
                    new Aeropuerto(aa.getOaci(), aa.getContinente(),
                            aa.getGmt(), aa.getCapacidadAlmacen(), 
                            aa.getLatitud(), aa.getLongitud()));
        }

        // --- 2. Vuelo ACS interno + mapa inverso id → VueloAlgoritmo ---
        Map<String, VueloAlgoritmo> mapaVueloPorId = new HashMap<>();
        List<Vuelo> vuelosACS = new ArrayList<>();
        for (VueloAlgoritmo va : input.getTodosLosVuelos()) {
            Vuelo v = new Vuelo(va.getOrigenOaci(), va.getDestinoOaci(),
                    va.getHoraSalida(), va.getHoraLlegada(), va.getCapacidad());
            vuelosACS.add(v);
            mapaVueloPorId.put(v.getId(), va);
        }

        // --- 3. Pedido ACS interno + mapa inverso id → EnvioAlgoritmo ---
        Map<String, EnvioAlgoritmo> mapaEnvioPorPedidoId = new HashMap<>();
        List<Pedido> pedidosACS = new ArrayList<>();
        for (EnvioAlgoritmo ea : input.getEnvios()) {
            AeropuertoAlgoritmo aOrig = input.getAeropuerto(ea.getOrigenOaci());
            AeropuertoAlgoritmo aDest = input.getAeropuerto(ea.getDestinoOaci());
            int limiteHoras = (aOrig != null && aDest != null &&
                    aOrig.getContinente().equalsIgnoreCase(aDest.getContinente())) ? 24 : 48;
            String idUnico = ea.getOrigenOaci() + "-" + ea.getId();
            Pedido p = new Pedido(idUnico, ea.getOrigenOaci(), ea.getDestinoOaci(),
                    ea.getFechaHoraRegistro(), ea.getCantidadMaletas(), ea.getClienteId());
            p.setTiempoLimite(ea.getFechaHoraRegistro().plusHours(limiteHoras));
            pedidosACS.add(p);
            mapaEnvioPorPedidoId.put(idUnico, ea);
        }

        // --- 4. Input ACS interno ---
        PlanificationProblemInputACS inputACS = new PlanificationProblemInputACS(
            mapaAeropuertosACS, 
            vuelosACS, 
            pedidosACS, 
            capVuelos,
            capAlmacenes
        );

        // --- 5. Ejecutar ACS ---
        PlanificationSolutionOutputACS solACS =
                AntColonySystem.ACS_TASF(inputACS, java.time.Instant.now(), tiempoMs);

        // --- 6. Traducir resultado → formato canónico ---
        Map<String, List<Asignacion>> asigPorPedido = new LinkedHashMap<>();
        for (Asignacion asig : solACS.getAsignaciones()) {
            asigPorPedido
                    .computeIfAbsent(asig.getPedido().getId(), k -> new ArrayList<>())
                    .add(asig);
        }

        for (Map.Entry<String, List<Asignacion>> entry : asigPorPedido.entrySet()) {
            EnvioAlgoritmo envio = mapaEnvioPorPedidoId.get(entry.getKey());
            if (envio == null) continue;

            List<VueloAlgoritmo> vuelosUsados      = new ArrayList<>();
            List<LocalDateTime>  fechasVuelo        = new ArrayList<>();
            List<Integer>        snapCapUsada       = new ArrayList<>(); // snapshot por vuelo
            List<Integer>        snapOcupAlmacen    = new ArrayList<>(); // snapshot por vuelo

            LocalDateTime tiempoActual   = envio.getFechaHoraRegistro();
            LocalDateTime llegadaFinal   = tiempoActual;
            LocalDateTime llegadaAlOrigen = envio.getFechaHoraRegistro();

            for (Asignacion a : entry.getValue()) {
                VueloAlgoritmo va = mapaVueloPorId.get(a.getVuelo().getId());
                if (va == null) continue;

                LocalDateTime salida = tiempoActual.with(va.getHoraSalida());
                if (salida.isBefore(tiempoActual)) salida = salida.plusDays(1);
                LocalDateTime llegada = salida.with(va.getHoraLlegada());
                if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

                // Clave de vuelo (misma que AG: origen-destino-horaSalida-fecha)
                String claveVuelo = va.getOrigenOaci() + "-" + va.getDestinoOaci()
                        + "-" + va.getHoraSalida() + "-" + salida.toLocalDate();

                // ── Snapshot de capacidad ANTES de esta asignación ───────────────
                // capRestante actual = capacidad ya reducida por asignaciones previas
                int ocupacionActualVuelo = capVuelos.getOrDefault(claveVuelo, 0);
                snapCapUsada.add(ocupacionActualVuelo);

                // ── Snapshot de almacén de origen en hora de salida ──────────────
                String claveAlmacen = va.getOrigenOaci()
                        + "-" + salida.toLocalDate()
                        + "-" + salida.getHour();
                // Actualizamos almacén ANTES de tomar snapshot para incluir esta maleta
                actualizarOcupacionAlmacen(va.getOrigenOaci(), llegadaAlOrigen, salida,
                        envio.getCantidadMaletas(), capAlmacenes);
                snapOcupAlmacen.add(capAlmacenes.getOrDefault(claveAlmacen, 0));

                // ── Actualizar capacidad restante del vuelo ───────────────────────
                capVuelos.put(claveVuelo, ocupacionActualVuelo + envio.getCantidadMaletas());

                vuelosUsados.add(va);
                fechasVuelo.add(salida);

                tiempoActual  = llegada.plusMinutes(VueloSelector.HANDLING_MINUTES);
                llegadaFinal  = tiempoActual;
                llegadaAlOrigen = llegada;
            }

            if (!vuelosUsados.isEmpty()) {
                output.agregarRuta(envio, new ResultadoRuta(
                        llegadaFinal, vuelosUsados, fechasVuelo));
            }
        }

        input.getOcupacionGlobalVuelos().putAll(capVuelos);
        input.getOcupacionGlobalAlmacenes().putAll(capAlmacenes);

        output.calcularMetricaUnificada(input.getMapaAeropuertos());
        output.setEstadoCapacidadesVuelos(capVuelos);
        output.setEstadoOcupacionAlmacenes(capAlmacenes);
        return output;
    }

    /** Replica AlgoritmoGenetico.actualizarOcupacionAlmacen exactamente. */
    private static void actualizarOcupacionAlmacen(
            String oaci, LocalDateTime llegada, LocalDateTime salida,
            int cantidadMaletas, Map<String, Integer> mapaAlmacenes) {
        LocalDateTime cursor = llegada.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime fin    = salida.truncatedTo(ChronoUnit.HOURS);
        while (!cursor.isAfter(fin)) {
            String clave = oaci + "-" + cursor.toLocalDate() + "-" + cursor.getHour();
            mapaAlmacenes.merge(clave, cantidadMaletas, Integer::sum);
            cursor = cursor.plusHours(1);
        }
    }
}
