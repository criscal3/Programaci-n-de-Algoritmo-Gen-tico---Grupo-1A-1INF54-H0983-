import java.util.*;

/**
 * Encapsula la entrada del problema de planificación para ambos algoritmos.
 * Usa las estructuras de datos del Algoritmo Genético (AeropuertoAlgoritmo,
 * VueloAlgoritmo, EnvioAlgoritmo) como base canónica.
 */
public class PlanificationProblemInput {

    private Map<String, AeropuertoAlgoritmo> aeropuertos;
    private Map<String, List<VueloAlgoritmo>> vuelosPorOrigen;
    private List<VueloAlgoritmo> todosLosVuelos;
    private List<EnvioAlgoritmo> envios;

    // Ocupación global acumulada entre bloques (clave: origen-destino-horaSalida-fecha)
    private Map<String, Integer> ocupacionGlobalVuelos;
    // Ocupación acumulada de almacenes (clave: oaciAlmacen, valor: array de ocupación por minuto)
    private Map<String, int[]> ocupacionGlobalAlmacenes;

    public PlanificationProblemInput() {
        this.aeropuertos            = new HashMap<>();
        this.vuelosPorOrigen        = new HashMap<>();
        this.todosLosVuelos         = new ArrayList<>();
        this.envios                 = new ArrayList<>();
        this.ocupacionGlobalVuelos  = new HashMap<>();
        this.ocupacionGlobalAlmacenes = new HashMap<>();
    }

    // ---- Aeropuertos ----

    public void agregarAeropuerto(AeropuertoAlgoritmo a) {
        aeropuertos.put(a.getOaci(), a);
    }

    public AeropuertoAlgoritmo getAeropuerto(String oaci) {
        return aeropuertos.get(oaci);
    }

    public Map<String, AeropuertoAlgoritmo> getMapaAeropuertos() {
        return aeropuertos;
    }

    // ---- Vuelos ----

    public void agregarVuelo(VueloAlgoritmo v) {
        todosLosVuelos.add(v);
        vuelosPorOrigen.computeIfAbsent(v.getOrigenOaci(), k -> new ArrayList<>()).add(v);
    }

    public List<VueloAlgoritmo> getTodosLosVuelos() {
        return todosLosVuelos;
    }

    public List<VueloAlgoritmo> getVuelosDesdeOrigen(String origenOaci) {
        return vuelosPorOrigen.getOrDefault(origenOaci, new ArrayList<>());
    }

    // ---- Envíos ----

    public void agregarEnvio(EnvioAlgoritmo e) {
        envios.add(e);
    }

    public void setEnvios(List<EnvioAlgoritmo> lista) {
        this.envios = new ArrayList<>(lista);
    }

    public List<EnvioAlgoritmo> getEnvios() {
        return envios;
    }

    // ---- Ocupación global de vuelos ----

    public int getOcupacionGlobalVuelo(String claveVuelo) {
        return ocupacionGlobalVuelos.getOrDefault(claveVuelo, 0);
    }

    public Map<String, Integer> getOcupacionGlobalVuelos() {
        return ocupacionGlobalVuelos;
    }

    // ---- Ocupación global de almacenes ----

    public Map<String, int[]> getOcupacionGlobalAlmacenes() {
        return ocupacionGlobalAlmacenes;
    }

    /**
     * Crea un sub-input que comparte aeropuertos, vuelos y ocupaciones globales
     * por referencia, pero tiene su propia lista de envíos.
     */
    public PlanificationProblemInput crearSubInput(List<EnvioAlgoritmo> subEnvios) {
        PlanificationProblemInput sub = new PlanificationProblemInput();
        sub.aeropuertos             = this.aeropuertos;
        sub.vuelosPorOrigen         = this.vuelosPorOrigen;
        sub.todosLosVuelos          = this.todosLosVuelos;
        sub.envios                  = new ArrayList<>(subEnvios);
        sub.ocupacionGlobalVuelos   = this.ocupacionGlobalVuelos;
        sub.ocupacionGlobalAlmacenes = this.ocupacionGlobalAlmacenes;
        return sub;
    }
}
