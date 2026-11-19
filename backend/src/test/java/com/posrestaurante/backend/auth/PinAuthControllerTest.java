package com.posrestaurante.backend.auth;

import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import com.posrestaurante.backend.asistencia.RegistroAsistenciaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PinAuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    RegistroAsistenciaRepository registroAsistenciaRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void staffListExcludesDuenoAndInactiveUsers() throws Exception {
        Usuario dueno = TestUsuarios.dueno("otro-dueno", "hash", true);
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "pin", true);
        Usuario chefInactivo = TestUsuarios.staff("Mario", Rol.CHEF, "pin", false);
        usuarioRepository.save(dueno);
        usuarioRepository.save(mesero);
        usuarioRepository.save(chefInactivo);

        mockMvc.perform(get("/api/auth/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Luis"));
    }

    @Test
    void correctPinLogsInAndRegistersOneAttendanceEntry() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"4821\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(registroAsistenciaRepository.countByUsuarioId(mesero.getId())).isEqualTo(1);
    }

    @Test
    void wrongPinIsRejectedAndRegistersNoAttendance() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(registroAsistenciaRepository.countByUsuarioId(mesero.getId())).isZero();
    }

    @Test
    void inactiveStaffCannotLoginEvenWithCorrectPin() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), false);
        usuarioRepository.save(mesero);

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"4821\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(registroAsistenciaRepository.countByUsuarioId(mesero.getId())).isZero();
    }

    @Test
    void unknownUsuarioIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + UUID.randomUUID() + "\",\"pin\":\"4821\"}"))
                .andExpect(status().isUnauthorized());
    }

}
