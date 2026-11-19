package com.posrestaurante.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    List<Usuario> findByActivoTrueAndRolNot(Rol rol);

}
