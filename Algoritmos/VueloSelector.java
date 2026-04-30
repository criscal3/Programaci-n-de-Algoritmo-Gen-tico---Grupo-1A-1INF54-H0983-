import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Selector de vuelo para el ACS (Ant Colony System).
 * Implementa la heurística y regla de transición del paper de Schyns.
 * Usa PlanificationProblemInputACS internamente.
 */
public class VueloSelector {

    private static final Random random = new Random(11111L);

    public static Vuelo seleccionarSiguienteVuelo(
            Pedido p, Ruta ruta, Map<String, Double> feromonas,
            double tau0, PlanificationProblemInputACS input, Instant reloj) {

        List<Vuelo> Ni = candidatosViables(p, ruta, input);
        if (Ni == null || Ni.isEmpty()) return null;

        final double q0   = 0.3;
        final double beta = 2.0;

        double[] valor     = new double[Ni.size()];
        double   sumaValor = 0.0;
        int      mejorIdx  = 0;
        double   mejorVal  = -1.0;

        for (int idx = 0; idx < Ni.size(); idx++) {
            Vuelo j = Ni.get(idx);
            
            // 1. Cronología exacta usando LocalDateTime
            LocalDateTime disp = getDisponibilidadAbsoluta(p, ruta);
            
            // ¿Cuándo sale el vuelo? (Sumamos 1 día si sale mañana)
            LocalDateTime salida = disp.toLocalDate().atTime(j.getHoraSalida());
            if (salida.isBefore(disp)) salida = salida.plusDays(1);
            
            // ¿Cuándo llega el vuelo? (Sumamos 1 día si cruza la medianoche)
            LocalDateTime llegada = salida.toLocalDate().atTime(j.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

            // 2. El costo real (Rij): Horas transcurridas desde la creación hasta el aterrizaje
            // Esto incluye automáticamente el tiempo de espera en el aeropuerto y el tiempo de vuelo.
            double Rij = java.time.Duration.between(p.getTiempoCreacion(), llegada).toMinutes() / 60.0;

            // 3. Sumar la heurística espacial (Haversine)
            double costoRestante = estimarTiempoRestante(j.getDestino(), p.getDestino(), input);
            Rij += costoRestante;
            
            if (Rij <= 0) Rij = 0.001; // Seguridad por si Rij da 0
            
            // Inversamente proporcional: A menor tiempo total proyectado, más deseable es el vuelo
            double eta_ij = 1.0 / Rij;

            // 4. Leer feromonas usando la clave correcta
            String flightKey = j.getId() + "-" + salida.toLocalDate();
            double tau = feromonas.getOrDefault(flightKey, tau0);

            // 5. Calcular probabilidad
            valor[idx] = tau * Math.pow(eta_ij, beta);
            sumaValor += valor[idx];

            if (valor[idx] > mejorVal) {
                mejorVal = valor[idx];
                mejorIdx = idx;
            }
        }

        if (random.nextDouble() <= q0) return Ni.get(mejorIdx);

        if (sumaValor <= 0) return Ni.get(random.nextInt(Ni.size()));
        double tirada = random.nextDouble() * sumaValor;
        double acum   = 0.0;
        for (int idx = 0; idx < Ni.size(); idx++) {
            acum += valor[idx];
            if (acum >= tirada) return Ni.get(idx);
        }
        return Ni.get(Ni.size() - 1);
    }

    private static double estimarTiempoRestante(String nodoIntermedio, String destinoFinal, PlanificationProblemInputACS input) {
        if (nodoIntermedio.equals(destinoFinal)) return 0.0;

        Aeropuerto aInter = input.getAeropuerto(nodoIntermedio);
        Aeropuerto aDest = input.getAeropuerto(destinoFinal);
        if (aInter == null || aDest == null) return 12.0;

        // Fórmula de Haversine
        double lat1 = Math.toRadians(aInter.getLatitud());
        double lon1 = Math.toRadians(aInter.getLongitud());
        double lat2 = Math.toRadians(aDest.getLatitud());
        double lon2 = Math.toRadians(aDest.getLongitud());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanciaKm = 6371.0 * c; // Radio de la Tierra en Km

        // Asumiendo velocidad comercial de 950 km/h + 4 horas de penalidad por hacer escala
        return (distanciaKm / 950.0); 
    }

    // ---- Disponibilidad absoluta del pedido en su nodo actual ----

    /**
     * Tiempo mínimo de manipulación de maleta en cualquier aeropuerto:
     *   - En escala : la maleta debe estar al menos HANDLING_MINUTES en el almacén
     *                 antes de poder embarcar en el siguiente vuelo.
     *   - En destino: tiempo entre llegada y recogida por el cliente.
     * Se suma después de cada aterrizaje, por lo que candidatosViables y el BFS
     * multi-hop lo heredan automáticamente al llamar a este método.
     */
    public static final int HANDLING_MINUTES = 10;

    public static java.time.LocalDateTime getDisponibilidadAbsoluta(Pedido p, Ruta ruta) {
        java.time.LocalDateTime actual = p.getTiempoCreacion();
        for (Asignacion a : ruta.getAsignaciones()) {
            if (!a.getPedido().getId().equals(p.getId())) continue;
            Vuelo v = a.getVuelo();
            java.time.LocalDateTime salida = actual.with(v.getHoraSalida());
            if (salida.isBefore(actual)) salida = salida.plusDays(1);
            java.time.LocalDateTime llegada = salida.with(v.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);
            // Tiempo de manipulación: mínimo 10 min en almacén tras cada aterrizaje
            actual = llegada.plusMinutes(HANDLING_MINUTES);
        }
        return actual;
    }

    // ---- Candidatos viables ----

    public static List<Vuelo> candidatosViables(
            Pedido p, Ruta ruta, PlanificationProblemInputACS input) {

        String desde = ruta.getUbicacionActual(p);
        List<Vuelo> viables = new ArrayList<>();
        java.time.LocalDateTime disp = getDisponibilidadAbsoluta(p, ruta);

        for (Vuelo v : input.getVuelosDesdeLinea(desde)) {
            if (ruta.haVisitadoAeropuerto(p, v.getDestino())) continue;

            java.time.LocalDateTime salida = disp.with(v.getHoraSalida());
            if (salida.isBefore(disp)) salida = salida.plusDays(1);
            java.time.LocalDateTime llegada = salida.with(v.getHoraLlegada());
            if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

            if (llegada.isAfter(p.getTiempoLimite())) continue;
            
            String flightKey = v.getId() + "-" + salida.toLocalDate().toString();
            int usoGlobal = input.getOcupacionGlobalVuelos(flightKey);
            int usoLocal  = ruta.getOcupacionVuelo(flightKey);
            if (usoGlobal + usoLocal + p.getCantidadMaletas() > v.getCapacidad()) continue;

            LocalDateTime llegadaAlAlmacen = getDisponibilidadAbsoluta(p, ruta); // Cuando llega al aeropuerto
            LocalDateTime salidaDelAlmacen = salida; // Cuando sale el vuelo

            boolean almacenConEspacio = true;

            // Validar cada hora de espera
            for (LocalDateTime t = llegadaAlAlmacen.truncatedTo(ChronoUnit.HOURS); 
                !t.isAfter(salidaDelAlmacen); t = t.plusHours(1)) {
                
                String claveAlmacen = v.getOrigen() + "-" + t.toLocalDate() + "-" + t.getHour();
                
                // 1. Lo que ya estaba ocupado desde antes (histórico)
                int ocupacionGlobal = input.getOcupacionGlobalAlmacenes(claveAlmacen);
                
                // 2. Lo que esta hormiga ESPECÍFICA ya acomodó en este almacén 
                //    mientras construía la solución actual.
                int ocupacionLocal = ruta.getOcupacionAlmacen(claveAlmacen); 
                
                // Validamos la suma contra la capacidad máxima del almacén de origen
                if (ocupacionGlobal + ocupacionLocal + p.getCantidadMaletas() > input.getAeropuerto(v.getOrigen()).getCapacidad()) {
                    almacenConEspacio = false;
                    break;
                }
            }

            if (!almacenConEspacio) continue; // Si no cabe, descartar el vuelo

            viables.add(v);
        }
        return viables;
    }

}
