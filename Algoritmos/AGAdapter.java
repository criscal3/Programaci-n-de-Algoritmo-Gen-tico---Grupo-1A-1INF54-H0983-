import java.time.LocalDateTime;
import java.util.*;

/**
 * Adaptador del Algoritmo Genético.
 * Envuelve AlgoritmoGenetico para producir PlanificationSolutionOutput canónico.
 * Puebla los snapshots de capacidad y almacén en cada ResultadoRuta para
 * que Simulador pueda mostrarlos en el reporte y el log.
 */
public class AGAdapter {

    public static PlanificationSolutionOutput planificar(
            PlanificationProblemInput input, int tiempoSegundos) {

        PlanificationSolutionOutput output = new PlanificationSolutionOutput("AG");
        if (input.getEnvios().isEmpty()) {
            output.setMetricaUnificada(0.0);
            return output;
        }

        AlgoritmoGenetico ag = new AlgoritmoGenetico(
                input.getTodosLosVuelos(),
                input.getMapaAeropuertos());

        List<EnvioAlgoritmo> enviosPlanificados = ag.planificar(input.getEnvios(), tiempoSegundos);

        Map<String, ResultadoRuta> mapaRutas   = ag.getMejoresRutasActuales();
        Map<String, Integer> capVuelos          = ag.getEstadoCapacidadesFinal();
        Map<String, Integer> ocupAlmacenes      = ag.getOcupacionAlmacenesFisicos();

        for (EnvioAlgoritmo envio : enviosPlanificados) {
            String clave = envio.getOrigenOaci() + "-" + envio.getId();
            ResultadoRuta ruta = mapaRutas.get(clave);

            if (ruta != null) {
                // Poblar snapshots en la ResultadoRuta del AG usando los mapas
                // finales del bloque. El AG acumula las capacidades en orden
                // de procesamiento, por lo que el mapa refleja el estado al
                // finalizar todos los envíos del bloque.
                List<Integer> snapCap     = new ArrayList<>();
                List<Integer> snapAlmacen = new ArrayList<>();

                for (int i = 0; i < ruta.vuelosUsados.size(); i++) {
                    VueloAlgoritmo v     = ruta.vuelosUsados.get(i);
                    LocalDateTime salida = ruta.fechasVuelo.get(i);

                    // Capacidad usada: capacidad total menos restante
                    String claveV    = v.getOrigenOaci() + "-" + v.getDestinoOaci()
                            + "-" + v.getHoraSalida() + "-" + salida.toLocalDate();
                    int capRestante  = capVuelos.getOrDefault(claveV, v.getCapacidad());
                    snapCap.add(v.getCapacidad() - capRestante);

                    // Ocupación del almacén de origen en hora de salida
                    String claveA   = v.getOrigenOaci()
                            + "-" + salida.toLocalDate()
                            + "-" + salida.getHour();
                    snapAlmacen.add(ocupAlmacenes.getOrDefault(claveA, 0));
                }
            }

            output.agregarRuta(envio, ruta);
        }

        for (Map.Entry<String, Integer> e : ocupAlmacenes.entrySet())
            input.incrementarOcupacionGlobalAlmacen(e.getKey(), e.getValue());

        output.calcularMetricaUnificada(input.getMapaAeropuertos());
        output.setEstadoCapacidadesVuelos(capVuelos);
        output.setEstadoOcupacionAlmacenes(ocupAlmacenes);
        return output;
    }
}
