package common.util

import java.lang.*

class File {
    public static def writeFile(filename, data) {
        java.io.File file = new java.io.File(filename)
        file.write(data)
    }
}