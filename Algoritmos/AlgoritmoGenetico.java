import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

public class AlgoritmoGenetico {
    private int tamanoPoblacion = 10;
    private double probCruzamiento = 0.8;
    private double probMutacion = 0.1;
    private Random random = new Random(11111L);

    // Red de vuelos y aeropuertos para el decodificador
    private List<VueloAlgoritmo> vuelosDisponibles;
    private Map<String, AeropuertoAlgoritmo> mapaAeropuertos;
    private Map<String, ResultadoRuta> mejoresRutasActuales = new HashMap<>();
    private Map<String, Integer> estadoCapacidadesFinal = new HashMap<>();
    private Map<String, int[]> ocupacionAlmacenesFisicos = new HashMap<>();

    public Map<String, Integer> getEstadoCapacidadesFinal() {
        return estadoCapacidadesFinal;
    }

    public Map<String, ResultadoRuta> getMejoresRutasActuales() {
        return mejoresRutasActuales;
    }

    public Map<String, int[]> getOcupacionAlmacenesFisicos() {
        return ocupacionAlmacenesFisicos;
    }

    public AlgoritmoGenetico(List<VueloAlgoritmo> vuelosDisponibles, Map<String, AeropuertoAlgoritmo> mapaAeropuertos, Map<String, Integer> ocupacionVuelosHistorica,
                             Map<String, int[]> ocupacionAlmacenesHistorica) {
        this.vuelosDisponibles = vuelosDisponibles;
        this.mapaAeropuertos = mapaAeropuertos;
        this.estadoCapacidadesFinal = new HashMap<>(ocupacionVuelosHistorica);
        this.ocupacionAlmacenesFisicos = new HashMap<>(ocupacionAlmacenesHistorica);
    }

    public List<EnvioAlgoritmo> planificar(List<EnvioAlgoritmo> enviosDelSalto, int limiteTiempoSegundos) {
        
        List<Individuo> poblacion = inicializarPoblacion(enviosDelSalto);
        
        // Convertimos los segundos a milisegundos para mayor precisión
        long tiempoInicio = System.currentTimeMillis();
        long duracionMaximaMs = limiteTiempoSegundos * 1000L;

        while (true) {
            
            long tiempoTranscurrido = System.currentTimeMillis() - tiempoInicio;
            if (tiempoTranscurrido >= duracionMaximaMs) {
                break; // Rompemos el ciclo de generaciones prematuramente
            }

            for (Individuo ind : poblacion) {
                evaluarFitness(ind);
            }

            poblacion.sort(Comparator.comparingDouble(Individuo::getFitness));
            List<Individuo> nuevaPoblacion = nuevaGeneracion(poblacion);
            mutarPoblacion(nuevaPoblacion);
            poblacion = nuevaPoblacion;
        }

        // Evaluar la última generación (o la generación donde se detuvo) y retornar el mejor
        poblacion.forEach(this::evaluarFitness);
        poblacion.sort(Comparator.comparingDouble(Individuo::getFitness));
        
        System.out.println("   -> Mejor fitness alcanzado: " + poblacion.get(0).getFitness());
        
        // Guardamos las rutas finales del mejor individuo
        Individuo mejor = poblacion.get(0);
        mejoresRutasActuales.clear();
        //estadoCapacidadesFinal.clear();

        for (EnvioAlgoritmo envio : mejor.getCromosoma()) {
            // Simulamos la ruta descontando la capacidad del mapa global final
            ResultadoRuta res = simularRutaParaEnvio(envio, estadoCapacidadesFinal, new HashMap<>());
            
            if (res != null) {
                String claveUnica = envio.getOrigenOaci() + "-" + envio.getId();
                this.mejoresRutasActuales.put(claveUnica, res);
                
                // Actualizamos la capacidad en el mapa global tras asignar la ruta
                for (int i = 0; i < res.vuelosUsados.size(); i++) {
                    VueloAlgoritmo v = res.vuelosUsados.get(i);
                    String claveCap = v.getOrigenOaci() + "-" + v.getDestinoOaci() + "-" + v.getHoraSalida() + "-" + res.fechasVuelo.get(i).toLocalDate();
                    
                    int ocupacionActual = this.estadoCapacidadesFinal.getOrDefault(claveCap, 0);
                    this.estadoCapacidadesFinal.put(claveCap, ocupacionActual + envio.getCantidadMaletas());
                }

                for (int i = 0; i < res.vuelosUsados.size(); i++) {
                    String oaci = res.vuelosUsados.get(i).getOrigenOaci(); 
                    LocalDateTime llegadaAlAlmacen;
                    if (i == 0) {
                        // Para el primer tramo, la llegada al almacén de origen es el registro del envío
                        llegadaAlAlmacen = envio.getFechaHoraRegistro();
                    } else {
                        // Para escalas, calculamos la llegada del vuelo anterior
                        VueloAlgoritmo vAnterior = res.vuelosUsados.get(i - 1);
                        LocalDateTime salidaVueloAnterior = res.fechasVuelo.get(i - 1);
                        
                        // Calculamos la duración entre las horas programadas
                        long duracionMinutos = java.time.Duration.between(
                            vAnterior.getHoraSalida(), 
                            vAnterior.getHoraLlegada()
                        ).toMinutes();

                        // Ajuste por cruce de medianoche: 
                        // Si la llegada es numéricamente menor a la salida (ej: sale 23:00, llega 02:00)
                        if (duracionMinutos < 0) {
                            duracionMinutos += 1440; // Sumamos 24 horas en minutos
                        }

                        llegadaAlAlmacen = salidaVueloAnterior.plusMinutes(duracionMinutos);
                    }

                    LocalDateTime salidaDelAlmacen = res.fechasVuelo.get(i);
                    
                    // Bloquear el espacio en el mapa de ocupación
                    actualizarOcupacionAlmacen(oaci, llegadaAlAlmacen, salidaDelAlmacen, envio.getCantidadMaletas());
                }
            }
        }

        return mejor.getCromosoma();
    }

    private List<Individuo> inicializarPoblacion(List<EnvioAlgoritmo> envios) {
        List<Individuo> pob = new ArrayList<>();
        for (int i = 0; i < tamanoPoblacion; i++) {
            pob.add(new Individuo(envios));
        }
        return pob;
    }

    private void evaluarFitness(Individuo individuo) {
        double costoTotal = 0.0;
        Map<String, Integer> capacidadDinamica = new HashMap<>(this.estadoCapacidadesFinal);
        Map<String, int[]> almacenDinamico = new HashMap<>();

        final double PENALIDAD_MAX_VUELO = 120.0;   // Máximo 3 horas virtuales por vuelo lleno
        final double PENALIDAD_MAX_ALMACEN = 180.0; // Máximo 4 horas virtuales por almacén lleno

        for (EnvioAlgoritmo envio : individuo.getCromosoma()) {
            ResultadoRuta resultado = simularRutaParaEnvio(envio, capacidadDinamica, almacenDinamico); 
            
            if (resultado == null) {
                // Penalización por colapso (No hay ruta)
                costoTotal += 999999.0;
            } else {
                long minutosViaje = ChronoUnit.MINUTES.between(envio.getFechaHoraRegistro(), resultado.tiempoLlegadaFinal);
                long horasViaje = minutosViaje / 60;

                String contOrigen = mapaAeropuertos.get(envio.getOrigenOaci()).getContinente();
                String contDestino = mapaAeropuertos.get(envio.getDestinoOaci()).getContinente();
                
                // 24 horas si es el mismo continente, 48 si es distinto
                int limiteHorasSLA = contOrigen.equalsIgnoreCase(contDestino) ? 24 : 48;

                if (horasViaje > limiteHorasSLA) {
                    // Penalización por colapso (Llegó tarde)
                    costoTotal += 999999.0+PENALIDAD_MAX_ALMACEN+PENALIDAD_MAX_VUELO; 
                } else {
                    // Si está a tiempo, el costo es simplemente el tiempo de tránsito
                    costoTotal += minutosViaje;
                }

                double costoCongestion = 0.0;

                // Actualizar la capacidad
                for (int i = 0; i < resultado.vuelosUsados.size(); i++) {
                    VueloAlgoritmo v = resultado.vuelosUsados.get(i);
                    LocalDate diaVuelo = resultado.fechasVuelo.get(i).toLocalDate();
                    String clave = v.getOrigenOaci() + "-" + v.getDestinoOaci() + "-" + v.getHoraSalida() + "-" + diaVuelo;
                    int ocupacionDinActual = capacidadDinamica.getOrDefault(clave, 0);
                    capacidadDinamica.put(clave, ocupacionDinActual + envio.getCantidadMaletas());
                    
                    // --- A) PENALIZACIÓN POR CONGESTIÓN DEL VUELO ---
                    double ratioOcupacion = (double) (ocupacionDinActual + envio.getCantidadMaletas()) / v.getCapacidad();
                    costoCongestion += Math.pow(ratioOcupacion, 2) * PENALIDAD_MAX_VUELO;
                }

                for (int i = 0; i < resultado.vuelosUsados.size(); i++) {
                    String oaci = resultado.vuelosUsados.get(i).getOrigenOaci(); 
                    LocalDateTime llegadaAlAlmacen;
                    if (i == 0) {
                        llegadaAlAlmacen = envio.getFechaHoraRegistro();
                    } else {
                        VueloAlgoritmo vAnterior = resultado.vuelosUsados.get(i - 1);
                        LocalDateTime salidaVueloAnterior = resultado.fechasVuelo.get(i - 1);
                        long duracionMinutos = java.time.Duration.between(vAnterior.getHoraSalida(), vAnterior.getHoraLlegada()).toMinutes();
                        if (duracionMinutos < 0) duracionMinutos += 1440;
                        llegadaAlAlmacen = salidaVueloAnterior.plusMinutes(duracionMinutos);
                    }

                    LocalDateTime salidaDelAlmacen = resultado.fechasVuelo.get(i);
                    
                    // Actualizamos el mapa local/dinámico
                    ocuparAlmacenDinamico(oaci, llegadaAlAlmacen, salidaDelAlmacen, envio.getCantidadMaletas(), almacenDinamico);
                    // --- B) PENALIZACIÓN POR CONGESTIÓN DEL ALMACÉN ---
                    // Evaluamos qué tan lleno estaba el almacén justo en el minuto que embarcó
                    // Obtenemos el índice del minuto exacto
                    int idxMinuto = Main.getIndiceMinuto(salidaDelAlmacen);
                    
                    int[] arregloAlmacen = almacenDinamico.get(oaci);
                    if (arregloAlmacen != null) {
                        int ocupacionAlmacen = arregloAlmacen[idxMinuto];
                        int capMaxAlmacen = mapaAeropuertos.get(oaci).getCapacidadAlmacen();
                        
                        double ratioAlmacen = (double) ocupacionAlmacen / capMaxAlmacen;
                        costoCongestion += Math.pow(ratioAlmacen, 2) * PENALIDAD_MAX_ALMACEN;
                    }
                }

                // Se suma el tiempo virtual por congestión al costo total
                costoTotal += costoCongestion;
            }
        }
        individuo.setFitness(costoTotal);
    }

    private List<Individuo> nuevaGeneracion(List<Individuo> poblacionActual) {
        List<Individuo> siguientePoblacion = new ArrayList<>();
        // Elitismo: guardamos a los 2 mejores directamente
        siguientePoblacion.add(poblacionActual.get(0));
        siguientePoblacion.add(poblacionActual.get(1));

        while (siguientePoblacion.size() < tamanoPoblacion) {
            // Torneo simple para seleccionar padres
            Individuo padre1 = seleccionTorneo(poblacionActual);
            Individuo padre2 = seleccionTorneo(poblacionActual);

            // Cruzamiento OX (Order Crossover), ideal para permutaciones
            if (random.nextDouble() < probCruzamiento) {
                Individuo hijo = cruzarOX(padre1, padre2);
                siguientePoblacion.add(hijo);
            } else {
                siguientePoblacion.add(padre1);
            }
        }
        return siguientePoblacion;
    }

    private Individuo seleccionTorneo(List<Individuo> poblacion) {
        Individuo comp1 = poblacion.get(random.nextInt(poblacion.size()));
        Individuo comp2 = poblacion.get(random.nextInt(poblacion.size()));
        return comp1.getFitness() < comp2.getFitness() ? comp1 : comp2;
    }

    private Individuo cruzarOX(Individuo p1, Individuo p2) {
        // Asumiendo que el cromosoma es una lista de enteros que representan el orden de los envíos
        List<EnvioAlgoritmo> cromo1 = p1.getCromosoma();
        List<EnvioAlgoritmo> cromo2 = p2.getCromosoma();
        int size = cromo1.size();

        // 1. Elegir dos puntos de corte aleatorios
        // Usamos el objeto 'random' que ya tienes instanciado en tu clase
        int cut1 = this.random.nextInt(size);
        int cut2 = this.random.nextInt(size);

        // Asegurarnos de que cut1 sea menor o igual que cut2
        if (cut1 > cut2) {
            int temp = cut1;
            cut1 = cut2;
            cut2 = temp;
        }

        // Inicializar el cromosoma del descendiente con valores nulos
        List<EnvioAlgoritmo> descendienteCromo = new ArrayList<>(Collections.nCopies(size, null));
        Set<EnvioAlgoritmo> genesCopiados = new HashSet<>();

        // 2. Copiar el segmento del Padre 1 al descendiente
        for (int i = cut1; i <= cut2; i++) {
            EnvioAlgoritmo gen = cromo1.get(i);
            descendienteCromo.set(i, gen);
            genesCopiados.add(gen);
        }

        // 3. Llenar los espacios restantes usando el Padre 2
        // Empezamos a buscar en el Padre 2 justo después del segundo punto de corte
        int indexPadre2 = (cut2 + 1) % size;
        
        // Empezamos a llenar en el descendiente justo después del segundo punto de corte
        int indexDescendiente = (cut2 + 1) % size;

        // Iteramos tantas veces como el tamaño del cromosoma para revisar todo el Padre 2
        for (int i = 0; i < size; i++) {
            EnvioAlgoritmo genP2 = cromo2.get(indexPadre2);
            
            // Si el gen del Padre 2 NO está ya en el descendiente, lo insertamos
            if (!genesCopiados.contains(genP2)) {
                descendienteCromo.set(indexDescendiente, genP2);
                // Avanzamos el índice del descendiente de forma circular
                indexDescendiente = (indexDescendiente + 1) % size;
            }
            // Avanzamos el índice del Padre 2 de forma circular
            indexPadre2 = (indexPadre2 + 1) % size;
        }

        // Retornamos el nuevo individuo
        return new Individuo(descendienteCromo);
    }

    private void mutarPoblacion(List<Individuo> poblacion) {
        // Mutación por intercambio (Swap Mutation)
        for (int i = 2; i < poblacion.size(); i++) { // Evitar mutar la élite (índices 0 y 1)
            if (random.nextDouble() < probMutacion) {
                List<EnvioAlgoritmo> chromo = poblacion.get(i).getCromosoma();
                int idx1 = random.nextInt(chromo.size());
                int idx2 = random.nextInt(chromo.size());
                
                EnvioAlgoritmo temp = chromo.get(idx1);
                chromo.set(idx1, chromo.get(idx2));
                chromo.set(idx2, temp);
            }
        }
    }

    private ResultadoRuta simularRutaParaEnvio(EnvioAlgoritmo envio, Map<String, Integer> capacidadDinamica, Map<String, int[]> almacenDinamico) {
        PriorityQueue<NodoRuta> cola = new PriorityQueue<>();
        
        // Estado inicial
        double heuristicaInicial = estimarTiempoRestanteMinutos(envio.getOrigenOaci(), envio.getDestinoOaci());
        // Estado inicial: La maleta está lista para buscar vuelo 10 minutos DESPUÉS de registrarse
        LocalDateTime tiempoListoParaVolar = envio.getFechaHoraRegistro().plusMinutes(10);
        cola.add(new NodoRuta(envio.getOrigenOaci(), tiempoListoParaVolar, 0, heuristicaInicial));

        Map<String, LocalDateTime> mejorTiempoVisita = new HashMap<>();

        while (!cola.isEmpty()) {
            NodoRuta actual = cola.poll();

            if (actual.aeropuertoActual.equals(envio.getDestinoOaci())) {
                // actual.tiempoActual YA TIENE los 10 minutos sumados de la última llegada
                return new ResultadoRuta(actual.tiempoActual, actual.rutaUsada, actual.fechasVuelosUsados);
            }

            if (mejorTiempoVisita.containsKey(actual.aeropuertoActual) && 
                mejorTiempoVisita.get(actual.aeropuertoActual).isBefore(actual.tiempoActual)) {
                continue;
            }
            mejorTiempoVisita.put(actual.aeropuertoActual, actual.tiempoActual);

            for (VueloAlgoritmo vuelo : vuelosDisponibles) {
                if (!vuelo.getOrigenOaci().equals(actual.aeropuertoActual)) continue;

                LocalDateTime proximaSalida = calcularProximaSalida(actual.tiempoActual, vuelo.getHoraSalida());
                
                String claveCapacidad = vuelo.getOrigenOaci() + "-" + vuelo.getDestinoOaci() + "-" + vuelo.getHoraSalida() + "-" + proximaSalida.toLocalDate();
                int ocupacionActual = capacidadDinamica.getOrDefault(claveCapacidad, 0);
                if (ocupacionActual + envio.getCantidadMaletas() > vuelo.getCapacidad()) continue;

                LocalDateTime llegadaAlAlmacenOrigen = actual.rutaUsada.isEmpty() 
                    ? envio.getFechaHoraRegistro() 
                    : actual.tiempoActual.minusMinutes(10); 
            
                AeropuertoAlgoritmo aeroOrigen = mapaAeropuertos.get(vuelo.getOrigenOaci());
                
                if (!hayEspacioEnAlmacen(vuelo.getOrigenOaci(), llegadaAlAlmacenOrigen, proximaSalida, envio.getCantidadMaletas(), aeroOrigen.getCapacidadAlmacen(), almacenDinamico)) {
                    continue; // Descartar: Se desbordaría el almacén de origen esperando este vuelo
                }

                LocalDateTime llegada = calcularLlegada(proximaSalida, vuelo.getHoraSalida(), vuelo.getHoraLlegada());
                LocalDateTime proximoTiempoDisponible = llegada.plusMinutes(10); 

                if (!vuelo.getDestinoOaci().equals(envio.getDestinoOaci())) {
                    AeropuertoAlgoritmo aeroDestino = mapaAeropuertos.get(vuelo.getDestinoOaci());
                    LocalDateTime salidaProyectada = llegada.plusHours(2); 

                    boolean escalaTieneEspacio = hayEspacioEnAlmacen(
                        vuelo.getDestinoOaci(), 
                        llegada, 
                        salidaProyectada, 
                        envio.getCantidadMaletas(), 
                        aeroDestino.getCapacidadAlmacen(),
                        almacenDinamico 
                    );

                    if (!escalaTieneEspacio) {
                        continue; 
                    }
                }

                // A*
                // Calculamos g(n): Tiempo real transcurrido desde el inicio de la maleta hasta esta nueva escala
                long minutosG = ChronoUnit.MINUTES.between(envio.getFechaHoraRegistro(), proximoTiempoDisponible);
                
                // Calculamos h(n): Estimación desde la siguiente escala hasta el destino final
                double minutosH = estimarTiempoRestanteMinutos(vuelo.getDestinoOaci(), envio.getDestinoOaci());
                
                // f(n) = g(n) + h(n)
                double costoF = minutosG + minutosH;

                NodoRuta siguiente = new NodoRuta(vuelo.getDestinoOaci(), proximoTiempoDisponible, minutosG, costoF);
                siguiente.rutaUsada.addAll(actual.rutaUsada);
                siguiente.fechasVuelosUsados.addAll(actual.fechasVuelosUsados);
                
                siguiente.rutaUsada.add(vuelo);
                siguiente.fechasVuelosUsados.add(proximaSalida);

                cola.add(siguiente);
            }
        }
        return null;
    }

    // Fórmula de Haversine para obtener la distancia en KM
    private double calcularDistancia(AeropuertoAlgoritmo origen, AeropuertoAlgoritmo destino) {
        double lat1 = Math.toRadians(origen.getLatitud());
        double lon1 = Math.toRadians(origen.getLongitud());
        double lat2 = Math.toRadians(destino.getLatitud());
        double lon2 = Math.toRadians(destino.getLongitud());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.pow(Math.sin(dLat / 2), 2) + 
                   Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        
        double c = 2 * Math.asin(Math.sqrt(a));
        double radioTierraKm = 6371.0;
        
        return radioTierraKm * c;
    }

    // Heurística h(n)
    private double estimarTiempoRestanteMinutos(String origenOaci, String destinoOaci) {
        AeropuertoAlgoritmo origen = mapaAeropuertos.get(origenOaci);
        AeropuertoAlgoritmo destino = mapaAeropuertos.get(destinoOaci);
        
        if (origen == null || destino == null) return 0.0;

        double distanciaKm = calcularDistancia(origen, destino);
        double velocidadMaxAvionKmh = 950.0; // Velocidad optimista
        
        // (Distancia / Velocidad) * 60 minutos
        return (distanciaKm / velocidadMaxAvionKmh) * 60.0; 
    }

    private LocalDateTime calcularProximaSalida(LocalDateTime tiempoActualListo, LocalTime horaSalidaVueloGmt) {
        LocalDate fechaActual = tiempoActualListo.toLocalDate();
        LocalTime horaActual = tiempoActualListo.toLocalTime();

        // Si el vuelo sale hoy más tarde (o exactamente a esta hora), lo tomamos hoy
        if (!horaSalidaVueloGmt.isBefore(horaActual)) {
            return LocalDateTime.of(fechaActual, horaSalidaVueloGmt);
        } else {
            // Si la hora del vuelo ya pasó, hay que tomar el del día siguiente
            return LocalDateTime.of(fechaActual.plusDays(1), horaSalidaVueloGmt);
        }
    }

    private LocalDateTime calcularLlegada(LocalDateTime fechaHoraSalida, LocalTime horaSalidaGmt, LocalTime horaLlegadaGmt) {
        LocalDateTime llegada = LocalDateTime.of(fechaHoraSalida.toLocalDate(), horaLlegadaGmt);
        
        // Si la hora de llegada numérica es menor a la de salida, significa que el vuelo cruzó la medianoche GMT
        if (horaLlegadaGmt.isBefore(horaSalidaGmt)) {
            llegada = llegada.plusDays(1);
        }
        return llegada;
    }

    /**
     * Registra la cantidad de maletas en el almacén de un aeropuerto específico 
     * durante cada hora que permanezcan allí.
     */
    private void actualizarOcupacionAlmacen(String oaci, LocalDateTime llegada, LocalDateTime salida, int cantidadMaletas) {
        // 1. Obtener los índices exactos (minutos desde la FECHA_BASE)
        int idxInicio = Main.getIndiceMinuto(llegada);
        int idxFin    = Main.getIndiceMinuto(salida);

        // 2. Obtener el arreglo del mapa global (ahora es Map<String, int[]>)
        // Si el arreglo no existe para este aeropuerto, lo inicializamos
        int[] almacen = this.ocupacionAlmacenesFisicos.computeIfAbsent(oaci, 
                k -> new int[Main.getIndiceMinuto(Main.FECHA_FIN_SIM.plusHours(72)) + 1]); // +1 para incluir el último minuto

        // 3. Iterar sobre el arreglo primitivo y sumar la cantidad
        for (int i = idxInicio; i < idxFin; i++) {
            almacen[i] += cantidadMaletas;
        }
    }

    private void ocuparAlmacenDinamico(String oaci, LocalDateTime llegada, LocalDateTime salida, int cantidad, Map<String, int[]> almacenDinamico) {
        
        int idxInicio = Main.getIndiceMinuto(llegada);
        int idxFin = Main.getIndiceMinuto(salida);
        
        int[] local = almacenDinamico.computeIfAbsent(oaci, k -> new int[Main.getIndiceMinuto(Main.FECHA_FIN_SIM.plusHours(72)) + 1]);
        
        for (int i = idxInicio; i < idxFin; i++) {
            local[i] += cantidad;
        }
    }

    private boolean hayEspacioEnAlmacen(String oaci, LocalDateTime llegada, LocalDateTime salida, int cantidad, int capacidadMax, Map<String, int[]> almacenDinamico) {
        
        int idxInicio = Main.getIndiceMinuto(llegada);
        int idxFin = Main.getIndiceMinuto(salida);
        
        int[] global = ocupacionAlmacenesFisicos.get(oaci);
        int[] local = almacenDinamico.get(oaci);
        
        for (int i = idxInicio; i < idxFin; i++) {
            int usoGlobal = (global != null) ? global[i] : 0;
            int usoLocal = (local != null) ? local[i] : 0;
            
            if (usoGlobal + usoLocal + cantidad > capacidadMax) {
                return false; 
            }
        }
        return true;
    }

}
