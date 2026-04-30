public class Aeropuerto {
    private String id;
    private String continente; // "America del Sur", "Europa", etc o simplificado a AM/EU
    private int husoHorario;   // e.g. -5, 2
    private int capacidad;     // 500 - 800
    private double latitud;   // en grados, e.g. -34.6037
    private double longitud;  // en grados, e.g. -58.3816

    public Aeropuerto(String id, String continente, int husoHorario, int capacidad, double latitud, double longitud) {
        this.id = id;
        this.continente = continente;
        this.husoHorario = husoHorario;
        this.capacidad = capacidad;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getId() { return id; }
    public String getContinente() { return continente; }
    public int getHusoHorario() { return husoHorario; }
    public int getCapacidad() { return capacidad; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
}
