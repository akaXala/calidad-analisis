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
        File base = new File(this.baseDirectory);
        if (!base.exists()) {
            base.mkdirs();
        }
    }

    public String getDirectorioActual() {
        StringBuilder actual = new StringBuilder(baseDirectory);
        for (String dir : directories) {
            actual.append("/").append(dir);
        }
        return actual.toString();
    }

    public List<String> listarArchivos() {
        List<String> lista = new ArrayList<>();
        File[] files = new File(getDirectorioActual()).listFiles();
        if (files != null) {
            for (File file : files) {
                lista.add(file.getName());
            }
        }
        return lista;
    }

    public boolean moverDirectorio(String argument) {
        if (argument.equals("..")) {
            return retrocederDirectorio();
        }
        return avanzarDirectorio(argument);
    }

    private boolean retrocederDirectorio() {
        if (!directories.empty()) {
            directories.pop();
            return true;
        }
        return false;
    }

    private boolean avanzarDirectorio(String argument) {
        File file = new File(getDirectorioActual() + "/" + argument);
        if (file.exists() && file.isDirectory()) {
            directories.push(argument);
            return true;
        }
        return false;
    }

    public boolean crearArchivo(String fileName) throws IOException {
        return new File(getDirectorioActual() + "/" + fileName).createNewFile();
    }

    public boolean crearCarpeta(String folderName) {
        return new File(getDirectorioActual() + "/" + folderName).mkdir();
    }

    public boolean renombrar(String name, String newName) {
        String dir = getDirectorioActual();
        return new File(dir + "/" + name).renameTo(new File(dir + "/" + newName));
    }

    public boolean eliminarArchivo(String fileName) {
        File file = new File(getDirectorioActual() + "/" + fileName);
        return file.exists() && file.isFile() && file.delete();
    }

    public boolean eliminarCarpeta(String folderName) {
        File folder = new File(getDirectorioActual() + "/" + folderName);
        return folder.exists() && folder.isDirectory() && eliminarRecursivo(folder);
    }

    private boolean eliminarRecursivo(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!eliminarRecursivo(file)) return false;
                } else {
                    if (!file.delete()) return false; // Fix: usar el resultado de delete()
                }
            }
        }
        return folder.delete();
    }

    public File obtenerArchivo(String fileName) {
        return new File(getDirectorioActual() + "/" + fileName);
    }

    // ---------------------------- MANEJO DE ARCHIVOS ZIP ---------------------------- //

    public boolean comprimirCarpeta(String folderName, String destZipPath) {
        File folder = new File(getDirectorioActual() + "/" + folderName);
        if (!folder.exists() || !folder.isDirectory()) return false;

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(destZipPath))) {
            comprimirContenido(folder, folder.getName(), zipOut);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void comprimirContenido(File file, String relativeName, ZipOutputStream zipOut) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    comprimirContenido(subFile, relativeName + "/" + subFile.getName(), zipOut);
                }
            }
        } else {
            escribirArchivoEnZip(file, relativeName, zipOut);
        }
    }

    private void escribirArchivoEnZip(File file, String relativeName, ZipOutputStream zipOut) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            zipOut.putNextEntry(new ZipEntry(relativeName));
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zipOut.write(buffer, 0, length);
            }
            zipOut.closeEntry();
        }
    }

    public boolean descomprimirCarpeta(String zipFilePath, String destFolderPath) {
        File destDir = new File(destFolderPath);
        if (!destDir.exists() && !destDir.mkdirs()) return false;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!procesarEntradaZip(destDir, entry, zis)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarEntradaZip(File destDir, ZipEntry entry, ZipInputStream zis) throws IOException {
        File file = new File(destDir, entry.getName());

        // --- FIX DE SEGURIDAD: Prevenir "Zip Slip" ---
        String destDirPath = destDir.getCanonicalPath();
        String destFilePath = file.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Violación de seguridad (Zip Slip): Intento de escape de directorio detectado.");
        }
        // ---------------------------------------------

        if (entry.isDirectory()) {
            return file.exists() || file.mkdirs();
        } else {
            File parentDir = file.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) return false;
            extraerArchivo(zis, file);
            zis.closeEntry();
            return true;
        }
    }

    private void extraerArchivo(ZipInputStream zis, File file) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = zis.read(buffer)) > 0) {
                bos.write(buffer, 0, count);
            }
        }
    }
}