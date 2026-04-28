import java.util.*;

/**
 * Encapsula la solución de planificación producida por cualquiera de los
 * dos algoritmos (AG o ACS).
 *
 * Mapea cada EnvioAlgoritmo a su ResultadoRuta calculado, junto con
 * métricas de calidad de la solución.
 */
public class PlanificationSolutionOutput {

    /** Mapa de (origenOaci + "-" + idEnvio) -> ResultadoRuta */
    private final Map<String, ResultadoRuta> mapaRutas;

    /** Lista de envíos en el orden en que fueron planificados */
    private final List<EnvioAlgoritmo> enviosPlanificados;

    /** Estado de capacidades de vuelos al final de este bloque */
    private final Map<String, Integer> estadoCapacidadesVuelos;

    /** Estado de ocupación de almacenes al final de este bloque */
    private final Map<String, Integer> estadoOcupacionAlmacenes;

    /** Fitness / responsiveness del mejor individuo o colonia */
    private double metricaUnificada;

    /** Nombre del algoritmo que generó esta solución */
    private String algoritmo;

    public PlanificationSolutionOutput(String algoritmo) {
        this.algoritmo               = algoritmo;
        this.mapaRutas               = new LinkedHashMap<>();
        this.enviosPlanificados      = new ArrayList<>();
        this.estadoCapacidadesVuelos = new HashMap<>();
        this.estadoOcupacionAlmacenes = new HashMap<>();
        this.metricaUnificada          = Double.MAX_VALUE;
    }

    // ---- Rutas ----

    public void agregarRuta(EnvioAlgoritmo envio, ResultadoRuta ruta) {
        String clave = envio.getOrigenOaci() + "-" + envio.getId();
        mapaRutas.put(clave, ruta);
        if (!enviosPlanificados.contains(envio)) {
            enviosPlanificados.add(envio);
        }
    }

    public ResultadoRuta getRuta(EnvioAlgoritmo envio) {
        return mapaRutas.get(envio.getOrigenOaci() + "-" + envio.getId());
    }

    public Map<String, ResultadoRuta> getMapaRutas() {
        return mapaRutas;
    }

    public List<EnvioAlgoritmo> getEnviosPlanificados() {
        return enviosPlanificados;
    }

    // ---- Capacidades y almacenes ----

    public void setEstadoCapacidadesVuelos(Map<String, Integer> estado) {
        this.estadoCapacidadesVuelos.putAll(estado);
    }

    public void setEstadoOcupacionAlmacenes(Map<String, Integer> estado) {
        this.estadoOcupacionAlmacenes.putAll(estado);
    }

    public Map<String, Integer> getEstadoCapacidadesVuelos() {
        return estadoCapacidadesVuelos;
    }

    public Map<String, Integer> getEstadoOcupacionAlmacenes() {
        return estadoOcupacionAlmacenes;
    }

    // ---- Métrica unificada ----

    /**
     * Calcula y almacena la métrica de calidad unificada para ambos algoritmos:
     *
     *   métrica = promedio de [ (horaLlegada − horaRegistro) / SLA ]
     *
     * donde SLA = 24h (1440 min) para mismo continente, 48h (2880 min) para distinto.
     *
     * Interpretación:
     *   0.0  → entregas instantáneas (imposible en práctica)
     *   < 1.0 → todos los envíos dentro del SLA (mejor zona)
     *   = 1.0 → todos los envíos llegaron exactamente en el límite del SLA
     *   > 1.0 → hay envíos que superaron el SLA (zona de colapso)
     *
     * Solo se incluyen envíos que tienen ruta y alcanzaron su destino.
     * Menor valor = mejor solución, y es directamente comparable entre AG y ACS.
     *
     * @param mapaAeropuertos Necesario para determinar el continente de origen y destino.
     */
    public void calcularMetricaUnificada(java.util.Map<String, AeropuertoAlgoritmo> mapaAeropuertos) {
        double sumaRatio = 0.0;
        int    conteo    = 0;

        for (EnvioAlgoritmo envio : enviosPlanificados) {
            ResultadoRuta ruta = getRuta(envio);
            if (ruta == null) continue;

            // Solo envíos que llegaron al destino correcto
            if (ruta.vuelosUsados.isEmpty()) continue;
            String destinoAlcanzado =
                    ruta.vuelosUsados.get(ruta.vuelosUsados.size() - 1).getDestinoOaci();
            if (!destinoAlcanzado.equals(envio.getDestinoOaci())) continue;

            // Determinar SLA según continentes
            AeropuertoAlgoritmo aOrig = mapaAeropuertos.get(envio.getOrigenOaci());
            AeropuertoAlgoritmo aDest = mapaAeropuertos.get(envio.getDestinoOaci());
            int slaMinutos = (aOrig != null && aDest != null &&
                    aOrig.getContinente().equalsIgnoreCase(aDest.getContinente()))
                    ? 24 * 60   // 1440 min — mismo continente
                    : 48 * 60;  // 2880 min — distinto continente

            long minutos = java.time.temporal.ChronoUnit.MINUTES.between(
                    envio.getFechaHoraRegistro(), ruta.tiempoLlegadaFinal);

            sumaRatio += (double) minutos / slaMinutos;
            conteo++;
        }

        this.metricaUnificada = conteo > 0 ? sumaRatio / conteo : Double.MAX_VALUE;
    }

    public double getMetricaUnificada() { return metricaUnificada; }
    public void setMetricaUnificada(double m) { this.metricaUnificada = m; }

    public String getAlgoritmo() { return algoritmo; }

    // ---- Estadísticas rápidas ----

    public int totalEnvios() {
        return enviosPlanificados.size();
    }

    public long totalMaletas() {
        return enviosPlanificados.stream()
                .mapToLong(EnvioAlgoritmo::getCantidadMaletas).sum();
    }

    public int enviosConRuta() {
        return (int) enviosPlanificados.stream()
                .filter(e -> getRuta(e) != null).count();
    }
}
