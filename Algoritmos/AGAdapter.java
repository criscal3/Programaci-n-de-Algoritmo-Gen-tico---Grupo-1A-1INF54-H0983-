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
            output.setPromedioConsumoSLA(0.0);
            return output;
        }

        // 1. INYECTAMOS LOS MAPAS HISTÓRICOS AL ALGORITMO
        AlgoritmoGenetico ag = new AlgoritmoGenetico(
                input.getTodosLosVuelos(),
                input.getMapaAeropuertos(),
                input.getOcupacionGlobalVuelos(),
                input.getOcupacionGlobalAlmacenes()
        );

        List<EnvioAlgoritmo> enviosPlanificados = ag.planificar(input.getEnvios(), tiempoSegundos);
        enviosPlanificados.sort(Comparator.comparing(EnvioAlgoritmo::getFechaHoraRegistro));

        Map<String, ResultadoRuta> mapaRutas   = ag.getMejoresRutasActuales();
        Map<String, Integer> capVuelos          = ag.getEstadoCapacidadesFinal(); // Ahora es "Ocupación de Vuelos"
        Map<String, int[]> ocupAlmacenes      = ag.getOcupacionAlmacenesFisicos();

        for (EnvioAlgoritmo envio : enviosPlanificados) {
            String clave = envio.getOrigenOaci() + "-" + envio.getId();
            ResultadoRuta ruta = mapaRutas.get(clave);
            output.agregarRuta(envio, ruta);
        }

        // 2. ACTUALIZAMOS EL INPUT REEMPLAZANDO LOS MAPAS DIRECTAMENTE 
        input.getOcupacionGlobalAlmacenes().putAll(ocupAlmacenes);
        input.getOcupacionGlobalVuelos().putAll(capVuelos);

        output.calcularPromedioConsumoSLA(input.getMapaAeropuertos());
        output.setEstadoCapacidadesVuelos(capVuelos);
        output.setEstadoOcupacionAlmacenes(ocupAlmacenes);
        return output;
    }
}
