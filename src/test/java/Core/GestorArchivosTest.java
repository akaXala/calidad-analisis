package Core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class GestorArchivosTest {

    private GestorArchivos gestor;
    private final String CARPETA_PRUEBAS = "./Carpeta_Test_Temporal";

    @BeforeEach
    public void setup() {
        // ARRANGE global: Inicializamos nuestro gestor en una carpeta aislada
        gestor = new GestorArchivos(CARPETA_PRUEBAS);
    }

    @AfterEach
    public void tearDown() {
        // LIMPIEZA global: Borramos la carpeta temporal
        eliminarCarpetaFisica(new File(CARPETA_PRUEBAS));
    }

    // ==========================================
    // PRUEBAS DE CAMINOS FELICES (ÉXITO)
    // ==========================================

    @Test
    public void testCrearArchivo_Exito() throws IOException {
        // Arrange
        String nombreArchivo = "archivo_prueba_1.txt";

        // Act
        boolean resultado = gestor.crearArchivo(nombreArchivo);

        // Assert
        assertTrue(resultado, "El método debe retornar true al crear un archivo nuevo");
        File archivoFisico = new File(CARPETA_PRUEBAS + "/" + nombreArchivo);
        assertTrue(archivoFisico.exists(), "El archivo físico debe existir en el disco");
    }

    @Test
    public void testCrearCarpeta_YMoverDirectorio() {
        // Arrange
        String nombreCarpeta = "subcarpeta";

        // Act
        boolean carpetaCreada = gestor.crearCarpeta(nombreCarpeta);
        boolean movidoAdelante = gestor.moverDirectorio(nombreCarpeta);
        String rutaDespuesDeMover = gestor.getDirectorioActual();

        boolean movidoAtras = gestor.moverDirectorio("..");
        String rutaOriginal = gestor.getDirectorioActual();

        // Assert
        assertTrue(carpetaCreada, "Debe crear la carpeta exitosamente");
        assertTrue(movidoAdelante, "Debe poder entrar a la nueva carpeta");
        assertTrue(rutaDespuesDeMover.endsWith(nombreCarpeta), "La ruta actual debe terminar con el nombre de la carpeta");
        assertTrue(movidoAtras, "Debe poder regresar al directorio padre");
        assertEquals(CARPETA_PRUEBAS, rutaOriginal, "La ruta debe volver a ser la raíz de pruebas");
    }

    @Test
    public void testRenombrarCarpeta() {
        // Arrange
        String nombreCarpeta = "carpeta";
        String nuevoNombreCarpeta = "supercarpeta";
        boolean carpetaCreada = gestor.crearCarpeta(nombreCarpeta);

        // Act
        boolean carpetaRenombrada = gestor.renombrar(nombreCarpeta, nuevoNombreCarpeta);
        List<String> archivos = gestor.listarArchivos();

        // Assert
        assertTrue(carpetaCreada, "Debe crear la carpeta exitosamente");
        assertTrue(carpetaRenombrada, "Debe poder cambiar el nombre de la carpeta");
        assertTrue(archivos.contains("supercarpeta"), "La lista debe contener el nuevo nombre de la carpeta");
    }

    @Test
    public void testEliminarCarpeta() throws IOException {
        // Arrange
        gestor.crearCarpeta("carpetera");

        // Act
        boolean carpetaEliminada = gestor.eliminarCarpeta("carpetera");
        List<String> archivos = gestor.listarArchivos();

        // Assert
        assertTrue(carpetaEliminada, "Debe eliminar la carpeta exitosamente");
        assertEquals(0, archivos.size(), "Debe haber exactamente 0 carpetas listadas");
    }

    @Test
    void testEliminarRecursivo() throws IOException {
        // Arrange
        String root = "Raiz";
        String subRoot = "subRaiz";
        gestor.crearCarpeta(root);
        gestor.moverDirectorio(root);
        gestor.crearArchivo("archivo1.txt");
        gestor.crearCarpeta(subRoot);
        gestor.moverDirectorio(subRoot);
        gestor.crearArchivo("archivo2.txt");
        gestor.moverDirectorio(".."); // Salimos de subRaiz
        gestor.moverDirectorio(".."); // Salimos de Raiz

        // Recopilamos estado antes de borrar para validar
        gestor.moverDirectorio(root);
        List<String> archivosAntes = gestor.listarArchivos();
        gestor.moverDirectorio("..");

        // Act
        boolean eliminadoRecursivo = gestor.eliminarCarpeta(root);
        boolean movidoARaiz = gestor.moverDirectorio(root);
        List<String> archivosDespues = gestor.listarArchivos();

        // Assert
        assertEquals(2, archivosAntes.size(), "Debe haber exactamente 2 archivos dentro de la raiz");
        assertTrue(eliminadoRecursivo, "Debe retornar true si elimina la carpeta");
        assertFalse(movidoARaiz, "Debe ser incapaz de entrar a la carpeta");
        assertEquals(0, archivosDespues.size(), "Debe haber exactamente 0 carpetas y archivos listados");
    }

    @Test
    public void testListarArchivos() throws IOException {
        // Arrange
        gestor.crearArchivo("doc1.txt");
        gestor.crearArchivo("doc2.txt");

        // Act
        List<String> archivos = gestor.listarArchivos();

        // Assert
        assertEquals(2, archivos.size(), "Debe haber exactamente 2 archivos listados");
        assertTrue(archivos.contains("doc1.txt"), "La lista debe contener doc1.txt");
        assertTrue(archivos.contains("doc2.txt"), "La lista debe contener doc2.txt");
    }

    @Test
    public void testComprimirCarpeta_Exito() throws IOException {
        // Arrange
        String nombreCarpeta = "carpeta_zip";
        gestor.crearCarpeta(nombreCarpeta);
        gestor.moverDirectorio(nombreCarpeta);
        gestor.crearArchivo("archivo.txt");
        gestor.moverDirectorio("..");
        String zipDestino = gestor.getDirectorioActual() + "/carpeta.zip";

        // Act
        boolean carpetaComprimida = gestor.comprimirCarpeta(nombreCarpeta, zipDestino);
        List<String> archivos = gestor.listarArchivos();

        // Assert
        assertTrue(carpetaComprimida, "Debe comprimir la carpeta exitosamente");
        assertTrue(archivos.contains("carpeta.zip"), "La lista debe contener carpeta.zip");
    }

    @Test
    public void testDescomprimirCarpeta_Exito() throws IOException {
        // Arrange
        String carpetaOrigen = "origen_zip";
        gestor.crearCarpeta(carpetaOrigen);
        gestor.moverDirectorio(carpetaOrigen);
        gestor.crearArchivo("secreto.txt");
        gestor.moverDirectorio("..");

        String rutaZip = gestor.getDirectorioActual() + "/paquete.zip";
        gestor.comprimirCarpeta(carpetaOrigen, rutaZip);
        String rutaDestino = gestor.getDirectorioActual() + "/destino_extraccion";

        // Act
        boolean descomprimido = gestor.descomprimirCarpeta(rutaZip, rutaDestino);

        // Assert
        assertTrue(descomprimido, "Debe retornar true al descomprimir el ZIP exitosamente");

        gestor.moverDirectorio("destino_extraccion");
        assertTrue(gestor.listarArchivos().contains(carpetaOrigen), "El contenido descomprimido debe tener la carpeta original");
    }

    // ==========================================
    // PRUEBAS DE CAMINOS TRISTES (FALLOS CONTROLADOS)
    // ==========================================

    @Test
    public void testCrearArchivo_FallaSiYaExiste() throws IOException {
        // Arrange
        String nombreArchivo = "archivo_duplicado.txt";
        gestor.crearArchivo(nombreArchivo); // Creamos el archivo la primera vez

        // Act
        boolean resultadoSegundaVez = gestor.crearArchivo(nombreArchivo);

        // Assert
        assertFalse(resultadoSegundaVez, "El método debe retornar false si se intenta crear un archivo que ya existe");
    }

    @Test
    public void testMoverDirectorio_FallaSiNoExiste() {
        // Arrange
        String carpetaInexistente = "carpeta_fantasma";

        // Act
        boolean resultado = gestor.moverDirectorio(carpetaInexistente);

        // Assert
        assertFalse(resultado, "Debe retornar false al intentar entrar a una carpeta que no existe");
    }

    @Test
    public void testMoverDirectorio_FallaAlRetrocederDesdeRaiz() {
        // Arrange
        // Al instanciar el gestor, ya estamos en la raíz (Carpeta_Test_Temporal)

        // Act
        boolean resultado = gestor.moverDirectorio("..");

        // Assert
        assertFalse(resultado, "Debe retornar false al intentar retroceder si la pila ya está vacía (en la raíz)");
    }

    @Test
    public void testRenombrar_FallaSiOriginalNoExiste() {
        // Arrange
        String nombreOriginal = "archivo_inexistente.txt";
        String nombreNuevo = "nuevo_nombre.txt";

        // Act
        boolean resultado = gestor.renombrar(nombreOriginal, nombreNuevo);

        // Assert
        assertFalse(resultado, "Debe retornar false si se intenta renombrar un archivo o carpeta que no existe");
    }

    @Test
    public void testEliminarArchivo_FallaSiNoExiste() {
        // Arrange
        String archivoInexistente = "fantasma.txt";

        // Act
        boolean resultado = gestor.eliminarArchivo(archivoInexistente);

        // Assert
        assertFalse(resultado, "Debe retornar false si el archivo a eliminar no existe en el disco");
    }

    @Test
    public void testEliminarCarpeta_FallaSiNoExiste() {
        // Arrange
        String carpetaInexistente = "carpeta_fantasma";

        // Act
        boolean resultado = gestor.eliminarCarpeta(carpetaInexistente);

        // Assert
        assertFalse(resultado, "Debe retornar false si la carpeta a eliminar no existe");
    }

    @Test
    public void testComprimirCarpeta_FallaSiCarpetaNoExiste() {
        // Arrange
        String carpetaInexistente = "carpeta_fantasma";
        String zipDestino = gestor.getDirectorioActual() + "/paquete.zip";

        // Act
        boolean resultado = gestor.comprimirCarpeta(carpetaInexistente, zipDestino);

        // Assert
        assertFalse(resultado, "Debe retornar false si se intenta comprimir una carpeta inexistente");
    }

    @Test
    public void testDescomprimirCarpeta_FallaSiZipNoExiste() {
        // Arrange
        String rutaZipInexistente = gestor.getDirectorioActual() + "/no_existo.zip";
        String rutaDestino = gestor.getDirectorioActual() + "/destino_fallido";

        // Act
        boolean resultado = gestor.descomprimirCarpeta(rutaZipInexistente, rutaDestino);

        // Assert
        assertFalse(resultado, "Debe retornar false si el ZIP de origen no se encuentra en el disco");
    }

    // --- Método auxiliar para limpiar el entorno ---
    private boolean eliminarCarpetaFisica(File folder) {
        if (!folder.exists()) return false;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    eliminarCarpetaFisica(file);
                } else {
                    file.delete();
                }
            }
        }
        return folder.delete();
    }
}