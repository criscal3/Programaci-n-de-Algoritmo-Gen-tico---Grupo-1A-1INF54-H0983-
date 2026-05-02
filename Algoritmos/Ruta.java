import java.util.List;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class Ruta {
    private List<Asignacion> asignaciones;
    private Map<String, Integer> ocupacionVuelos;
    private Map<String, int[]> ocupacionAlmacenes;
    private Map<String, String> estadoUbicacionPedido;
    private Map<String, Set<String>> aeropuertosVisitados;

    public Ruta() {
        this.asignaciones = new ArrayList<>();
        this.ocupacionVuelos = new HashMap<>();
        this.ocupacionAlmacenes = new HashMap<>();
        this.estadoUbicacionPedido = new HashMap<>();
        this.aeropuertosVisitados = new HashMap<>();
    }

    public Ruta(List<Asignacion> asignaciones) {
        this.asignaciones = new ArrayList<>(asignaciones);
        this.ocupacionVuelos = new HashMap<>();
        this.ocupacionAlmacenes = new HashMap<>();
        this.estadoUbicacionPedido = new HashMap<>();
        this.aeropuertosVisitados = new HashMap<>();
        for (Asignacion a : asignaciones) {
            this.ocupacionVuelos.put(a.getVuelo().getId(),
                    this.ocupacionVuelos.getOrDefault(a.getVuelo().getId(), 0) + a.getPedido().getCantidadMaletas());
            this.estadoUbicacionPedido.put(a.getPedido().getId(), a.getVuelo().getDestino());
            this.ocupacionAlmacenes.put(a.getVuelo().getOrigen(),
                    this.ocupacionAlmacenes.getOrDefault(a.getVuelo().getOrigen(), new int[Main.getIndiceMinuto(Main.FECHA_FIN_SIM.plusHours(72)) + 1]).clone());
            this.aeropuertosVisitados.computeIfAbsent(a.getPedido().getId(), k -> new HashSet<>())
                    .add(a.getVuelo().getDestino());
            this.aeropuertosVisitados.get(a.getPedido().getId()).add(a.getPedido().getOrigen());
        }
    }

    public void agregarAsignacion(Pedido p, Vuelo v) {
        java.time.LocalDateTime disp = VueloSelector.getDisponibilidadAbsoluta(p, this);
        java.time.LocalDateTime salida = disp.with(v.getHoraSalida());
        if (salida.isBefore(disp)) salida = salida.plusDays(1);
        String flightKey = v.getId() + "-" + salida.toLocalDate().toString();

        this.asignaciones.add(new Asignacion(p, v, flightKey));
        this.ocupacionVuelos.put(flightKey,
            this.ocupacionVuelos.getOrDefault(flightKey, 0) + p.getCantidadMaletas());
        this.estadoUbicacionPedido.put(p.getId(), v.getDestino());

        this.aeropuertosVisitados.computeIfAbsent(p.getId(), k -> new HashSet<>()).add(p.getOrigen());
        this.aeropuertosVisitados.get(p.getId()).add(v.getDestino());
    }

    public List<Asignacion> getAsignaciones() {
        return asignaciones;
    }

    public int getOcupacionVuelo(String vueloId) {
        return ocupacionVuelos.getOrDefault(vueloId, 0);
    }

    public String getUbicacionActual(Pedido p) {
        return estadoUbicacionPedido.getOrDefault(p.getId(), p.getOrigen());
    }

    public boolean haVisitadoAeropuerto(Pedido p, String aeropuertoId) {
        if (p.getOrigen().equals(aeropuertoId))
            return true;
        Set<String> visitados = aeropuertosVisitados.get(p.getId());
        return visitados != null && visitados.contains(aeropuertoId);
    }

    /** Ultimo vuelo asignado a este pedido, null si ninguno aun. O(1). */
    public Vuelo getUltimoVuelo(Pedido p) {
        // walk backwards through assignments to find last flight for this order
        for (int i = asignaciones.size() - 1; i >= 0; i--) {
            if (asignaciones.get(i).getPedido().getId().equals(p.getId())) {
                return asignaciones.get(i).getVuelo();
            }
        }
        return null;
    }

    public PlanificationSolutionOutputACS aPlanificationSolution() {
        return new PlanificationSolutionOutputACS(new ArrayList<>(this.asignaciones));
    }

    public void registrarUsoAlmacen(String oaci, LocalDateTime llegada, LocalDateTime salida, int cantidad) {
        // 1. Obtener los índices de inicio y fin en minutos globales
        int idxInicio = Main.getIndiceMinuto(llegada);
        int idxFin    = Main.getIndiceMinuto(salida);

        // 2. Obtener o inicializar el arreglo para este aeropuerto (OACI)
        // El tamaño debe cubrir hasta el final de la simulación (usualmente FECHA_FIN_SIM + margen)
        int[] almacen = this.ocupacionAlmacenes.computeIfAbsent(oaci, k -> 
            new int[Main.getIndiceMinuto(Main.FECHA_FIN_SIM.plusHours(72)) + 1]
        );

        // 3. Llenar el arreglo minuto a minuto
        // Usamos < idxFin porque en el último minuto (momento de la salida) 
        // la maleta ya deja de ocupar espacio en el almacén.
        for (int i = idxInicio; i < idxFin; i++) {
            almacen[i] += cantidad;
        }
    }

    public int[] getOcupacionAlmacen(String claveAlmacen) {
        return ocupacionAlmacenes.getOrDefault(claveAlmacen, new int[Main.getIndiceMinuto(Main.FECHA_FIN_SIM.plusHours(72)) + 1]);
    }

}
