package Core;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class GestorArchivos {
    private final String baseDirectory;
    private final Stack<String> directories;

    public GestorArchivos(String baseDirectory) {
        this.baseDirectory = baseDirectory;
        this.directories = new Stack<>();

        // Aseguramos que el directorio base exista al instanciar la clase
        File base = new File(this.baseDirectory);
        if (!base.exists()) {
            base.mkdirs();
        }
    }

    /**
     * Construye y retorna la ruta actual basándose en la pila de directorios.
     */
    public String getDirectorioActual() {
        StringBuilder actual = new StringBuilder(baseDirectory);
        for (String dir : directories) {
            actual.append("/").append(dir);
        }
        return actual.toString();
    }

    /**
     * Retorna una lista con los nombres de los archivos en el directorio actual.
     * Ideal para hacer un assert en JUnit (ej. assertTrue(lista.contains("miArchivo.txt")))
     */
    public List<String> listarArchivos() {
        List<String> lista = new ArrayList<>();
        File path = new File(getDirectorioActual());
        File[] files = path.listFiles();

        if (files != null) {
            for (File file : files) {
                lista.add(file.getName());
            }
        }
        return lista;
    }

    /**
     * Navega entre directorios (".." para retroceder, o el nombre de la carpeta para avanzar).
     * Retorna true si tuvo éxito, false si el directorio no existe o ya está en la raíz.
     */
    public boolean moverDirectorio(String argument) {
        if (argument.equals("..")) {
            if (!directories.empty()) {
                directories.pop();
                return true;
            }
            return false; // Ya estamos en la raíz
        } else {
            String newDirectory = getDirectorioActual() + "/" + argument;
            File file = new File(newDirectory);

            if (file.exists() && file.isDirectory()) {
                directories.push(argument);
                return true;
            }
            return false;
        }
    }

    public boolean crearArchivo(String fileName) throws IOException {
        String path = getDirectorioActual() + "/" + fileName;
        File file = new File(path);
        return file.createNewFile(); // Retorna true si lo creó, false si ya existía
    }

    public boolean crearCarpeta(String folderName) {
        String path = getDirectorioActual() + "/" + folderName;
        File folder = new File(path);
        return folder.mkdir();
    }

    public boolean renombrar(String name, String newName) {
        String actualDirectory = getDirectorioActual();
        File original = new File(actualDirectory + "/" + name);
        File nuevo = new File(actualDirectory + "/" + newName);
        return original.renameTo(nuevo);
    }

    public boolean eliminarArchivo(String fileName) {
        String path = getDirectorioActual() + "/" + fileName;
        File file = new File(path);
        return file.exists() && file.isFile() && file.delete();
    }

    public boolean eliminarCarpeta(String folderName) {
        String path = getDirectorioActual() + "/" + folderName;
        File folder = new File(path);
        return folder.exists() && folder.isDirectory() && eliminarRecursivo(folder);
    }

    private boolean eliminarRecursivo(File folder) {
        if (!folder.exists()) return false;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    eliminarRecursivo(file);
                } else {
                    file.delete();
                }
            }
        }
        return folder.delete();
    }

    /**
     * Retorna el objeto File para que las clases de Sockets puedan leer su tamaño y enviarlo.
     */
    public File obtenerArchivo(String fileName) {
        return new File(getDirectorioActual() + "/" + fileName);
    }

    // ---------------------------- MANEJO DE ARCHIVOS ZIP ---------------------------- //

    public boolean comprimirCarpeta(String folderName, String destZipPath) {
        File folder = new File(getDirectorioActual() + "/" + folderName);
        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(destZipPath))) {
            comprimirRecursivo(folder, folder.getName(), zipOut);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void comprimirRecursivo(File file, String relativeName, ZipOutputStream zipOut) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    comprimirRecursivo(subFile, relativeName + "/" + subFile.getName(), zipOut);
                }
            }
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                ZipEntry zipEntry = new ZipEntry(relativeName);
                zipOut.putNextEntry(zipEntry);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) >= 0) {
                    zipOut.write(buffer, 0, length);
                }
                zipOut.closeEntry();
            }
        }
    }

    public boolean descomprimirCarpeta(String zipFilePath, String destFolderPath) {
        File destDir = new File(destFolderPath);
        if (!destDir.exists() && !destDir.mkdirs()) return false;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    if (!file.exists() && !file.mkdirs()) return false;
                } else {
                    File parentDir = file.getParentFile();
                    if (!parentDir.exists() && !parentDir.mkdirs()) return false;

                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}