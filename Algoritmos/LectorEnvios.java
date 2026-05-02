import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LectorEnvios {

    private List<EnvioAlgoritmo> todosLosEnvios = new ArrayList<>();
    private int indiceLecturaActual = 0;
    private LocalDateTime relojSimulacion = null;

    /**
     * Carga todos los envíos y estandariza sus horas al huso horario GMT-0.
     * @param rutaCarpeta Ruta de la carpeta _envios_preliminar_
     * @param listaAeropuertos Lista de aeropuertos previamente leída para obtener los GMT
     */
    /*public void cargarTodosLosEnvios(String rutaCarpeta, List<AeropuertoAlgoritmo> listaAeropuertos) {
        todosLosEnvios.clear();
        indiceLecturaActual = 0;
        relojSimulacion = null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm");

        // 1. Crear un diccionario rápido (Map) de OACI -> GMT
        Map<String, Integer> mapaGmt = new HashMap<>();
        for (AeropuertoAlgoritmo aero : listaAeropuertos) {
            mapaGmt.put(aero.getOaci(), aero.getGmt());
        }

        
        
        //System.out.println("[LectorEnvios] Escaneando archivos y filtrando por fechas...");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(rutaCarpeta), "*.txt")) {
            for (Path path : stream) {
                String nombre = path.getFileName().toString();
                String origenOaci = nombre.substring(nombre.indexOf("_envios_") + 8, nombre.lastIndexOf("_"));
                
                // Obtener el GMT del aeropuerto origen (si no existe, asumimos 0 por seguridad)
                int gmtOrigen = mapaGmt.getOrDefault(origenOaci, 0);

                List<String> lineas = Files.readAllLines(path, StandardCharsets.ISO_8859_1);
                for (String linea : lineas) {
                    if (linea.trim().isEmpty()) continue;
                    String[] d = linea.split("-");
                    if (d.length >= 7) {
                        EnvioAlgoritmo envio = new EnvioAlgoritmo();
                        envio.setOrigenOaci(origenOaci);
                        envio.setId(d[0].trim());
                        
                        // 2. Leer la fecha local del txt
                        LocalDateTime fechaLocal = LocalDateTime.parse(d[1] + " " + d[2] + ":" + d[3], dtf);
                        
                        // 3. Convertir a GMT-0 (Tiempo Universal)
                        // Restamos el offset GMT. Ej: Local 10:00, GMT -5 -> 10 - (-5) = 15:00 GMT-0
                        LocalDateTime fechaGmt0 = fechaLocal.minusHours(gmtOrigen);
                        envio.setFechaHoraRegistro(fechaGmt0);
                        
                        envio.setDestinoOaci(d[4].trim());
                        envio.setCantidadMaletas(Integer.parseInt(d[5].trim()));
                        envio.setClienteId(d[6].trim());
                        todosLosEnvios.add(envio);
                    }
                }
            }
            
            // 4. Ordenar TODO usando la línea de tiempo universal (GMT-0)
            todosLosEnvios.sort(Comparator.comparing(EnvioAlgoritmo::getFechaHoraRegistro));
            
            if (!todosLosEnvios.isEmpty()) {
                relojSimulacion = todosLosEnvios.get(0).getFechaHoraRegistro();
            }
            
            System.out.println(">>> Total de envíos cargados y estandarizados a GMT-0: " + todosLosEnvios.size());

        } catch (IOException e) {
            System.err.println("Error cargando carpeta: " + e.getMessage());
        }
    }*/

    public void cargarTodosLosEnvios(String rutaCarpeta, List<AeropuertoAlgoritmo> listaAeropuertos, 
                                    LocalDateTime fechaInicio, LocalDateTime fechaFin) {
    
        todosLosEnvios.clear();
        this.indiceLecturaActual = 0;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm");

        // 1. Crear mapa rápido de OACI -> GMT para la conversión
        Map<String, Integer> mapaGmt = new HashMap<>();
        for (AeropuertoAlgoritmo a : listaAeropuertos) {
            mapaGmt.put(a.getOaci(), a.getGmt());
        }

        System.out.println("[LectorEnvios] Escaneando archivos y filtrando por fechas...");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(rutaCarpeta))) {
            for (Path path : stream) {
                // Usamos un Stream de líneas para no saturar la RAM si el archivo es gigante
                Files.lines(path, StandardCharsets.UTF_8).forEach(linea -> {
                    if (linea.trim().isEmpty()) return;

                    String[] partes = linea.split("-");
                    // Estructura: ID - YYYYMMDD - HH - mm - DESTINO - CANTIDAD - CLIENTE
                    
                    // Reconstruir el string de la fecha para el parseo
                    String fechaStr = partes[1] + " " + partes[2] + ":" + partes[3];
                    LocalDateTime fechaRegistroOriginal = LocalDateTime.parse(fechaStr, dtf);

                    // Extraer OACI de Origen del nombre del archivo (Ej. _envios_EKCH_.txt -> EKCH)
                    String nombreArchivo = path.getFileName().toString();
                    String oaciOrigen = nombreArchivo.split("_")[2]; 
                    
                    // Convertir la fecha a GMT-0
                    int gmt = mapaGmt.getOrDefault(oaciOrigen, 0);
                    LocalDateTime fechaGmt0 = fechaRegistroOriginal.minusHours(gmt);

                    // =================================================================
                    // FILTRO TEMPORAL CRÍTICO (Ignora los que están fuera de fecha)
                    // =================================================================
                    if (fechaInicio != null && fechaGmt0.isBefore(fechaInicio)) {
                        return; // return en un forEach de Stream equivale a 'continue'
                    }
                    if (fechaFin != null && fechaGmt0.isAfter(fechaFin)) {
                        return; 
                    }

                    // Si pasó el filtro, creamos el objeto y lo guardamos
                    String idEnvio = partes[0];
                    String oaciDestino = partes[4];
                    int cantidadMaletas = Integer.parseInt(partes[5]);

                    EnvioAlgoritmo envio = new EnvioAlgoritmo();
                    envio.setId(idEnvio);
                    envio.setOrigenOaci(oaciOrigen);
                    envio.setDestinoOaci(oaciDestino);
                    envio.setCantidadMaletas(cantidadMaletas);
                    envio.setFechaHoraRegistro(fechaGmt0);
                    envio.setClienteId(partes[6]);
                    todosLosEnvios.add(envio);
                });
            }
        } catch (IOException e) {
            System.err.println("[LectorEnvios] Error al leer la carpeta de envíos: " + e.getMessage());
        }

        // =================================================================
        // ORDENAMIENTO CRONOLÓGICO (Arregla el desorden de los archivos)
        // =================================================================
        System.out.println("[LectorEnvios] Ordenando cronológicamente " + todosLosEnvios.size() + " envíos válidos...");
        todosLosEnvios.sort(Comparator.comparing(EnvioAlgoritmo::getFechaHoraRegistro));

        // Configurar el reloj de la simulación
        if (fechaInicio != null) {
            this.relojSimulacion = fechaInicio;
        } else if (!todosLosEnvios.isEmpty()) {
            this.relojSimulacion = todosLosEnvios.get(0).getFechaHoraRegistro();
        }
    }

    /**
     * Extrae todos los envíos no planificados que caen dentro de la nueva ventana de tiempo (Sc).
     * * @param scEnMinutos Salto de Consumo expresado en unidades de tiempo (ej. minutos)
     * @return Lista de envíos que entraron al sistema en ese rango de tiempo
     */
    public List<EnvioAlgoritmo> obtenerEnviosPorVentanaDeTiempo(int scEnMinutos) {
        List<EnvioAlgoritmo> bloque = new ArrayList<>();
        
        if (indiceLecturaActual >= todosLosEnvios.size() || relojSimulacion == null) {
            return bloque; // Ya no hay más datos
        }

        // 1. Avanzamos el reloj de la simulación según el Salto de Consumo
        relojSimulacion = relojSimulacion.plusMinutes(scEnMinutos);

        // 2. Extraemos todos los envíos cuya fecha de registro sea MENOR O IGUAL al reloj actual
        while (indiceLecturaActual < todosLosEnvios.size()) {
            EnvioAlgoritmo envioActual = todosLosEnvios.get(indiceLecturaActual);
            
            // isAfter() verifica si la fecha del envío es estrictamente mayor que nuestro reloj.
            // Si no lo es (!isAfter), significa que ya ocurrió y debemos procesarlo.
            if (!envioActual.getFechaHoraRegistro().isAfter(relojSimulacion)) {
                bloque.add(envioActual);
                indiceLecturaActual++;
            } else {
                // Encontramos un envío del futuro (fuera de la ventana actual). 
                // Rompemos el ciclo y guardamos el índice para la próxima llamada.
                break;
            }
        }

        return bloque;
    }

    public boolean hayMasEnvios() {
        return indiceLecturaActual < todosLosEnvios.size();
    }
    
    public LocalDateTime getRelojSimulacion() {
        return relojSimulacion;
    }

    public void configurarRangoTemporal(LocalDateTime fechaInicio) {
        if (fechaInicio != null) {
            this.relojSimulacion = fechaInicio;
            
            // "Fast-forward": Avanzamos el índice ignorando las maletas que llegaron ANTES de esta fecha
            while (indiceLecturaActual < todosLosEnvios.size() && 
                   todosLosEnvios.get(indiceLecturaActual).getFechaHoraRegistro().isBefore(fechaInicio)) {
                indiceLecturaActual++;
            }
            System.out.println("\n[CONFIGURACIÓN] Simulación iniciará en: " + fechaInicio);
            System.out.println(" -> Se han omitido " + indiceLecturaActual + " envíos anteriores a esta fecha.");
        } else {
            // Comportamiento original: El reloj inicia con el envío más antiguo
            if (!todosLosEnvios.isEmpty()) {
                this.relojSimulacion = todosLosEnvios.get(0).getFechaHoraRegistro();
            }
            System.out.println("\n[CONFIGURACIÓN] Simulación iniciará desde el primer envío registrado: " + this.relojSimulacion);
        }
    }
}
