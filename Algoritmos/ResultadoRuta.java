import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de la planificación de ruta para un envío.
 *
 * Además de los vuelos y fechas, guarda snapshots de ocupación
 * en el momento de cada asignación para poder mostrarlos en el reporte:
 *   capacidadUsadaVuelo  → maletas ya asignadas a ese vuelo en ese momento
 *   ocupacionAlmacenOrigen → maletas en el almacén de origen en la hora de salida
 */
public class ResultadoRuta {
    LocalDateTime           tiempoLlegadaFinal;
    List<VueloAlgoritmo>    vuelosUsados;
    List<LocalDateTime>     fechasVuelo;

    /** Maletas asignadas al vuelo i en el momento de esta asignación (snapshot) */
    List<Integer> capacidadUsadaVuelo;

    /** Maletas en el almacén de origen del vuelo i en la hora de salida (snapshot) */
    List<Integer> ocupacionAlmacenOrigen;

    public ResultadoRuta(LocalDateTime tiempo,
                         List<VueloAlgoritmo> vuelos,
                         List<LocalDateTime> fechas) {
        this.tiempoLlegadaFinal   = tiempo;
        this.vuelosUsados         = vuelos;
        this.fechasVuelo          = fechas;
        int n = vuelos.size();
        this.capacidadUsadaVuelo  = new ArrayList<>(java.util.Collections.nCopies(n, 0));
        this.ocupacionAlmacenOrigen = new ArrayList<>(java.util.Collections.nCopies(n, 0));
    }

    public ResultadoRuta(LocalDateTime tiempo,
                         List<VueloAlgoritmo> vuelos,
                         List<LocalDateTime> fechas,
                         List<Integer> capUsada,
                         List<Integer> ocAlmacen) {
        this.tiempoLlegadaFinal   = tiempo;
        this.vuelosUsados         = vuelos;
        this.fechasVuelo          = fechas;
        this.capacidadUsadaVuelo  = capUsada;
        this.ocupacionAlmacenOrigen = ocAlmacen;
    }
}
