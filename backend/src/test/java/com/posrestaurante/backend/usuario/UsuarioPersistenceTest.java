package com.posrestaurante.backend.usuario;

import com.posrestaurante.backend.asistencia.RegistroAsistencia;
import com.posrestaurante.backend.asistencia.RegistroAsistenciaRepository;
import com.posrestaurante.backend.asistencia.TipoAsistencia;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    RegistroAsistenciaRepository registroAsistenciaRepository;

    @Test
    void savesAndReloadsADuenoByUsername() {
        Usuario dueno = TestUsuarios.dueno("ana", "hash", true);
        usuarioRepository.save(dueno);

        Optional<Usuario> found = usuarioRepository.findByUsernameAndActivoTrue("ana");
        assertThat(found).isPresent();
        assertThat(found.get().getRol()).isEqualTo(Rol.DUENO);
    }

    @Test
    void listsActiveNonDuenoStaffOnly() {
        Usuario dueno = TestUsuarios.dueno("admin", "hash", true);
        Usuario activoMesero = TestUsuarios.staff("Luis", Rol.MESERO, "pin", true);
        Usuario inactivoChef = TestUsuarios.staff("Mario", Rol.CHEF, "pin", false);
        usuarioRepository.save(dueno);
        usuarioRepository.save(activoMesero);
        usuarioRepository.save(inactivoChef);

        List<Usuario> staff = usuarioRepository.findByActivoTrueAndRolNot(Rol.DUENO);

        assertThat(staff).hasSize(1);
        assertThat(staff.get(0).getNombre()).isEqualTo("Luis");
    }

    @Test
    void savesRegistroAsistenciaLinkedToUsuario() {
        Usuario usuario = TestUsuarios.staff("Luis", Rol.MESERO, "pin", true);
        usuarioRepository.save(usuario);

        registroAsistenciaRepository.save(
                new RegistroAsistencia(usuario, Instant.now(), TipoAsistencia.ENTRADA));

        assertThat(registroAsistenciaRepository.countByUsuarioId(usuario.getId())).isEqualTo(1);
    }

}
