## Connect framework

The project is divided in three specific parts:

* Contacts App
* Connect Android background process
* Connect Backend (Includes the App Service layer and the Connect library)


### Contacts App

Includes the Contacts's app logic and android UI.

#### Modules:

* 'app' --> android UI + logic of the contacts app
* 'furszy_ui_lib' --> common android libraries

### Connect Android background process

Includes the platform of the Connect running on background.

#### Modules on folder "android-connect":

* 'connect_sdk_android'
* 'connect_sdk_android_client'
* 'connect_sdk_android_shared_library'

### Connect Backend

Pure java connection library with the Libertaria network.

#### Modules:

* 'lib_sdk'