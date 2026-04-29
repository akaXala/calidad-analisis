Feature: Pruebas de aceptación del Servidor de Archivos (TCP)

  Background:
    # ARRANGE general: Instanciamos nuestra clase puente de Java
    * def TcpClient = Java.type('Pruebas.KarateTcpClient')
    * def cliente = new TcpClient('127.0.0.1', 7777)

  # ==========================================
  # PRUEBAS DE CAMINOS FELICES (ÉXITO)
  # ==========================================

  Scenario: Crear una carpeta y verificar que el servidor responde correctamente
    # Arrange: Conectamos al servidor
    * cliente.conectar()

    # Act: Enviamos el comando mkdir
    * def respuesta = cliente.enviarComando('mkdir carpeta_aceptacion')

    # Assert: Validamos que el servidor nos confirme la creación
    * match respuesta contains 'Carpeta creada exitosamente'

    # Cleanup (Limpieza) y desconexión
    * cliente.enviarComando('rmdir carpeta_aceptacion')
    * cliente.desconectar()

  Scenario: Crear un archivo y listarlo en el directorio
    * cliente.conectar()

    # Act / Assert 1: Crear archivo
    * def resCrear = cliente.enviarComando('touch hola_karate.txt')
    * match resCrear contains 'Archivo creado exitosamente'

    # Act / Assert 2: Listar directorio
    * def resListar = cliente.enviarComando('ls')
    * match resListar contains 'hola_karate.txt'

    # Cleanup
    * cliente.enviarComando('rm hola_karate.txt')
    * cliente.desconectar()

  Scenario: Navegar entre directorios y validar la ruta actual
    * cliente.conectar()
    * cliente.enviarComando('mkdir test_navegacion')

    # Act 1: Entramos a la nueva carpeta
    * def resCdAdelante = cliente.enviarComando('cd test_navegacion')

    # Assert 1: Validamos que el servidor nos confirme la nueva ruta
    * match resCdAdelante contains 'Ruta actual:'
    * match resCdAdelante contains 'test_navegacion'

    # Act 2: Creamos un archivo allí dentro y listamos
    * cliente.enviarComando('touch archivo_interno.txt')
    * def resLs = cliente.enviarComando('ls')
    * match resLs contains 'archivo_interno.txt'

    # Act 3 / Assert 3: Volvemos a la raíz
    * def resCdAtras = cliente.enviarComando('cd ..')
    * match resCdAtras !contains 'test_navegacion'

    # Cleanup
    * cliente.enviarComando('rmdir test_navegacion')
    * cliente.desconectar()

  Scenario: Renombrar un archivo en el servidor
    * cliente.conectar()

    # Arrange: Creamos el archivo original
    * cliente.enviarComando('touch original.txt')

    # Act: Cambiamos el nombre
    * def resMv = cliente.enviarComando('mv original.txt modificado.txt')

    # Assert 1: Confirmación del servidor
    * match resMv contains 'Renombrado correctamente'

    # Assert 2: Verificamos que el cambio se refleje en la lista
    * def resLs = cliente.enviarComando('ls')
    * match resLs contains 'modificado.txt'
    * match resLs !contains 'original.txt'

    # Cleanup
    * cliente.enviarComando('rm modificado.txt')
    * cliente.desconectar()

  Scenario: Eliminar un archivo existente
    * cliente.conectar()

    # Arrange: Creamos un archivo para borrar
    * cliente.enviarComando('touch para_borrar.txt')

    # Act: Solicitamos la eliminación
    * def resRm = cliente.enviarComando('rm para_borrar.txt')

    # Assert: Verificamos el mensaje de éxito y listamos para confirmar
    * match resRm contains 'Archivo eliminado.'
    * def resLs = cliente.enviarComando('ls')
    * match resLs !contains 'para_borrar.txt'

    # Cleanup
    * cliente.desconectar()

  Scenario: Enviar un comando no reconocido
    * cliente.conectar()

    # Act: Enviamos basura o comando inexistente
    * def resInvalido = cliente.enviarComando('volar_hacia_el_sol')

    # Assert: El servidor debe advertir amablemente
    * match resInvalido contains 'Comando no reconocido'

    # Cleanup
    * cliente.desconectar()

  # ==========================================
  # PRUEBAS DE CAMINOS TRISTES (FALLOS CONTROLADOS)
  # ==========================================

  Scenario: Control de errores al pedir un archivo que no existe (cp)
    * cliente.conectar()

    # Act: Solicitamos descargar un archivo fantasma
    * def resCp = cliente.enviarComando('cp documento_secreto.pdf')

    # Assert: El servidor debe capturar el error y responder con texto (no colgarse)
    * match resCp contains 'El archivo no existe'

    # Cleanup
    * cliente.desconectar()

  Scenario: Control de errores al pedir una carpeta que no existe (cpdir)
    * cliente.conectar()

    # Act: Solicitamos comprimir y descargar una carpeta fantasma
    * def resCpdir = cliente.enviarComando('cpdir carpeta_secreta')

    # Assert: El servidor debe rechazar la compresión y avisar
    * match resCpdir contains 'La carpeta no existe o falló la compresión'

    # Cleanup
    * cliente.desconectar()

  Scenario: Falla controlada al crear carpeta duplicada
    * cliente.conectar()

    # Arrange: Creamos la carpeta por primera vez
    * cliente.enviarComando('mkdir carpeta_unica')

    # Act: Intentamos crearla de nuevo
    * def resMkdir = cliente.enviarComando('mkdir carpeta_unica')

    # Assert: El servidor avisa que no es posible
    * match resMkdir contains 'No se pudo crear (quizás ya existe)'

    # Cleanup
    * cliente.enviarComando('rmdir carpeta_unica')
    * cliente.desconectar()

  Scenario: Navegación bloqueada hacia atrás desde la raíz
    * cliente.conectar()

    # Arrange: Al conectarnos ya estamos en la raíz del servidor
    # Act: Intentamos ir más atrás de la raíz permitida
    * def resCd = cliente.enviarComando('cd ..')

    # Assert: El servidor debe frenar la acción
    * match resCd contains 'El directorio no existe o ya estás en la raíz.'

    # Cleanup
    * cliente.desconectar()

  Scenario: Falla controlada al eliminar un archivo inexistente
    * cliente.conectar()

    # Act: Mandamos borrar algo que no existe
    * def resRm = cliente.enviarComando('rm archivo_fantasma.txt')

    # Assert
    * match resRm contains 'No se pudo eliminar el archivo.'

    # Cleanup
    * cliente.desconectar()