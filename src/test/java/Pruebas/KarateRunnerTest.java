package Pruebas;

import com.intuit.karate.junit5.Karate;

public class KarateRunnerTest {

    @Karate.Test
    Karate testAll() {
        // Le indicamos a Karate la ruta donde están nuestros archivos .feature
        return Karate.run("classpath:Pruebas/archivos.feature");
    }
}