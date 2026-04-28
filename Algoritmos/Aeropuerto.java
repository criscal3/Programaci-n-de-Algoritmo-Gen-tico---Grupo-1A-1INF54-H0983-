public class Aeropuerto {
    private String id;
    private String continente; // "America del Sur", "Europa", etc o simplificado a AM/EU
    private int husoHorario;   // e.g. -5, 2
    private int capacidad;     // 500 - 800

    public Aeropuerto(String id, String continente, int husoHorario, int capacidad) {
        this.id = id;
        this.continente = continente;
        this.husoHorario = husoHorario;
        this.capacidad = capacidad;
    }

    public String getId() { return id; }
    public String getContinente() { return continente; }
    public int getHusoHorario() { return husoHorario; }
    public int getCapacidad() { return capacidad; }
}
