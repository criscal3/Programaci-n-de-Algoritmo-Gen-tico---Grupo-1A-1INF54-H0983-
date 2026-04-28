import java.time.LocalDateTime;
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

    public ResultadoRuta(LocalDateTime tiempo,
                         List<VueloAlgoritmo> vuelos,
                         List<LocalDateTime> fechas) {
        this.tiempoLlegadaFinal   = tiempo;
        this.vuelosUsados         = vuelos;
        this.fechasVuelo          = fechas;
    }

}
