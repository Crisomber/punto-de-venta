package com.posrestaurante.backend.testsupport;

import com.posrestaurante.backend.usuario.Estacion;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;

public final class TestUsuarios {

    private TestUsuarios() {
    }

    public static Usuario dueno(String username, String passwordHash, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setNombre(username);
        usuario.setRol(Rol.DUENO);
        usuario.setEstacion(null);
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordHash);
        usuario.setPinHash(null);
        usuario.setActivo(activo);
        return usuario;
    }

    public static Usuario staff(String nombre, Rol rol, String pinHash, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setRol(rol);
        usuario.setEstacion(rol == Rol.CHEF ? Estacion.P1 : null);
        usuario.setUsername(null);
        usuario.setPasswordHash(null);
        usuario.setPinHash(pinHash);
        usuario.setActivo(activo);
        return usuario;
    }

}
