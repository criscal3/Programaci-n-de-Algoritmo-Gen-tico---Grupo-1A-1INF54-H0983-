import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Individuo {
    private static final Random rand = new Random(11111L);
    // El cromosoma es el orden en que se procesarán los envíos del bloque actual
    private List<EnvioAlgoritmo> cromosoma;
    private double fitness;

    public Individuo(List<EnvioAlgoritmo> envios) {
        this.cromosoma = new ArrayList<>(envios);
        // Mezclamos para crear un individuo inicial aleatorio
        Collections.shuffle(this.cromosoma, rand);
        this.fitness = 0.0;
    }

    // Getters y Setters
    public List<EnvioAlgoritmo> getCromosoma() { return cromosoma; }
    public double getFitness() { return fitness; }
    public void setFitness(double fitness) { this.fitness = fitness; }
}
