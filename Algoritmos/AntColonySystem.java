import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACS adaptado según el paper "An Ant Colony System for Responsive Dynamic Vehicle Routing"
 * de M. Schyns (Gambardella et al. 1999).
 *
 * Usa PlanificationProblemInputACS y PlanificationSolutionOutputACS internamente.
 * El adaptador ACSAdapter se encarga de la traducción hacia/desde las estructuras del AG.
 */
public class AntColonySystem {

    public static PlanificationSolutionOutputACS ACS_TASF(
            PlanificationProblemInputACS input, Instant reloj, long maxTiempoMs) {

        int n = input.getPedidos().size();
        if (n == 0) {
            return new PlanificationSolutionOutputACS(new ArrayList<>());
        }

        // --- 1. Inicialización con solución greedy ---
        PlanificationSolutionOutputACS psiStar = construirSolucionInicial_FIFO(input, reloj);
        double Rstar = calcularResponsiveness(psiStar, input);
        double Tstar = calcularDistanciaTotal(psiStar);
        
        double tau0;
        
        tau0 = Rstar > 0 
                   ? 1.0 / (n * Math.max(Rstar / n, 0.01)) 
                   : 1.0 / Math.max(n, 1);

        Map<String, Double> feromonas = new ConcurrentHashMap<>();

        // --- 2. Parámetros del ACS ---
        final double rho       = 0.4;
        final int    mHormigas = 10;

        long tiempoInicio = System.currentTimeMillis();

        // --- 3. Bucle principal ---
        while (System.currentTimeMillis() - tiempoInicio < maxTiempoMs) {

            for (int h = 0; h < mHormigas; h++) {
                Ruta ruta = new Ruta();

                for (Pedido pedido : input.getPedidos()) {
                    
                    int saltosMaximos = 5; // Evitar bucles infinitos
                    int saltosActuales = 0;

                    // 2. LA HORMIGA CAMINA SALTO A SALTO HASTA LLEGAR AL DESTINO
                    while (!ruta.getUbicacionActual(pedido).equals(pedido.getDestino()) && saltosActuales < saltosMaximos) {
                        
                        Vuelo vuelo = VueloSelector.seleccionarSiguienteVuelo(
                                pedido, ruta, feromonas, tau0, input, reloj);
                        
                        if (vuelo == null) {
                            break; // Callejón sin salida (no hay capacidad o se venció el SLA)
                        }

                        // 3. Calculamos Tiempos
                        LocalDateTime llegadaAeropuerto = VueloSelector.getDisponibilidadAbsoluta(pedido, ruta);
                        LocalDateTime salidaDelVuelo = llegadaAeropuerto.toLocalDate().atTime(vuelo.getHoraSalida());
                        if (salidaDelVuelo.isBefore(llegadaAeropuerto)) {
                            salidaDelVuelo = salidaDelVuelo.plusDays(1);
                        }

                        // 4. Registramos Asignación y Almacén (Memoria Local)
                        String flightKey = vuelo.getId() + "-" + salidaDelVuelo.toLocalDate();
                        ruta.agregarAsignacion(pedido, vuelo);
                        ruta.registrarUsoAlmacen(vuelo.getOrigen(), llegadaAeropuerto, salidaDelVuelo, pedido.getCantidadMaletas());

                        // 5. ACTUALIZACIÓN LOCAL DE FEROMONAS (Crucial para ACS)
                        // Disminuye el "olor" de este vuelo para obligar a otras hormigas a buscar alternativas
                        double tauActual = feromonas.getOrDefault(flightKey, tau0);
                        feromonas.put(flightKey, (1 - rho) * tauActual + rho * tau0);

                        saltosActuales++;
                    }
                }

                ruta = busquedaLocalCROSS(ruta, input);

                double Rpsi = calcularResponsiveness(ruta.aPlanificationSolution(), input);
                double Tpsi = calcularDistanciaTotal(ruta.aPlanificationSolution());

                
                final double EPS = 1e-6;
                if (Rpsi < Rstar - EPS || (Math.abs(Rpsi - Rstar) < EPS && Tpsi < Tstar)) {
                    psiStar = ruta.aPlanificationSolution();
                    Rstar   = Rpsi;
                    Tstar   = Tpsi;
                }
            }

            // Actualización GLOBAL
            if (Rstar > 0) {
                for (Asignacion a : psiStar.getAsignaciones()) {
                    String key = a.getFlightKey(); 
                    double tauActual = feromonas.getOrDefault(key, tau0);
                    feromonas.put(key, (1 - rho) * tauActual + rho / Rstar);
                }
            }
        }

        return psiStar;
    }

    // =======================================================================
    //  Métricas
    // =======================================================================

    public static double calcularResponsiveness(
            PlanificationSolutionOutputACS sol, PlanificationProblemInputACS input) {

        if (sol == null || sol.getAsignaciones().isEmpty()) return Double.MAX_VALUE / 2;

        // 1. Agrupar las asignaciones por pedido en orden de ejecución
        Map<String, List<Vuelo>> rutasPorPedido = new HashMap<>();
        for (Asignacion a : sol.getAsignaciones()) {
            rutasPorPedido.computeIfAbsent(a.getPedido().getId(), k -> new ArrayList<>()).add(a.getVuelo());
        }

        double R = 0.0;

        for (Pedido p : input.getPedidos()) {
            List<Vuelo> vuelos = rutasPorPedido.get(p.getId());

            // Si el paquete no tiene ningún vuelo asignado (colapsó en el origen)
            if (vuelos == null || vuelos.isEmpty()) {
                R += 1_000_000.0; 
                continue;
            }

            // 2. Simular el viaje cronológico para obtener la hora EXACTA de llegada final
            LocalDateTime tiempoCursor = p.getTiempoCreacion();
            String ubicacionActual = p.getOrigen();

            for (Vuelo v : vuelos) {
                // Calcular salida de este salto
                LocalDateTime salida = tiempoCursor.toLocalDate().atTime(v.getHoraSalida());
                if (salida.isBefore(tiempoCursor)) {
                    salida = salida.plusDays(1);
                }
                
                // Calcular llegada de este salto
                LocalDateTime llegada = salida.toLocalDate().atTime(v.getHoraLlegada());
                if (llegada.isBefore(salida)) {
                    llegada = llegada.plusDays(1);
                }

                // Avanzar el reloj y la ubicación para el siguiente salto
                tiempoCursor = llegada;
                ubicacionActual = v.getDestino();
            }

            // 3. Penalidad masiva si la ruta lo dejó botado en un aeropuerto intermedio
            if (!ubicacionActual.equals(p.getDestino())) {
                R += 1_000_000.0;
                continue;
            }

            // 4. Cálculo real y exacto: Horas totales desde Creación hasta Destino Final
            double horasTotales = java.time.Duration.between(p.getTiempoCreacion(), tiempoCursor).toMinutes() / 60.0;
            R += Math.max(0, horasTotales);
        }

        return R;
    }

    public static double calcularDistanciaTotal(PlanificationSolutionOutputACS sol) {
        if (sol == null) return Double.MAX_VALUE;
        double t = 0.0;
        for (Asignacion a : sol.getAsignaciones()) {
            Vuelo v = a.getVuelo();
            double s = v.getHoraSalida().toSecondOfDay() / 3600.0;
            double l = v.getHoraLlegada().toSecondOfDay() / 3600.0;
            if (l < s) l += 24.0;
            t += (l - s);
        }
        return t;
    }

    // =======================================================================
    //  Solución inicial greedy (FIFO)
    // =======================================================================

    private static PlanificationSolutionOutputACS construirSolucionInicial_FIFO(
            PlanificationProblemInputACS input, Instant reloj) {

        Ruta rutaTemp = new Ruta();
        for (Pedido p : input.getPedidos()) {
            int saltos = 0;
            while (!rutaTemp.getUbicacionActual(p).equals(p.getDestino()) && saltos < 20) {
                List<Vuelo> vuelos = VueloSelector.candidatosViables(p, rutaTemp, input);
                if (vuelos == null || vuelos.isEmpty()) break;
                Vuelo elegido = null;
                for (Vuelo v : vuelos) {
                    elegido = v;
                    if (v.getDestino().equals(p.getDestino())) break;
                }
                if (elegido == null) break;
                rutaTemp.agregarAsignacion(p, elegido);
                saltos++;
            }
        }
        Logger.info("ACS - Solución inicial FIFO construida.");
        return rutaTemp.aPlanificationSolution();
    }

    // =======================================================================
    //  Auxiliares
    // =======================================================================

    private static Ruta busquedaLocalCROSS(Ruta ruta, PlanificationProblemInputACS input) {
        List<Asignacion> limpias = new ArrayList<>();
        for (Pedido p : input.getPedidos()) {
            List<Asignacion> rp = new ArrayList<>();
            for (Asignacion a : ruta.getAsignaciones()) {
                if (a.getPedido().getId().equals(p.getId())) rp.add(a);
            }
            for (int i = 0; i < rp.size(); i++) {
                for (int j = rp.size() - 1; j > i; j--) {
                    if (rp.get(i).getVuelo().getOrigen().equals(rp.get(j).getVuelo().getDestino())) {
                        rp.subList(i + 1, j + 1).clear();
                        break;
                    }
                }
            }
            limpias.addAll(rp);
        }
        return new Ruta(limpias);
    }

    /*private static double duracionHorasUTC(Vuelo v) {
        double s = v.getHoraSalida().toSecondOfDay()  / 3600.0;
        double l = v.getHoraLlegada().toSecondOfDay() / 3600.0;
        
        double dur = l - s;
        if (dur < 0) {
            dur += 24.0; 
        }
        
        return dur;
    }*/
}
