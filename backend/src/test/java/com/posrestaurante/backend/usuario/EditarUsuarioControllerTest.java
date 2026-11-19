package com.posrestaurante.backend.usuario;

import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EditarUsuarioControllerTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoRenamesUsuario() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis Actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luis Actualizado"));
    }

    @Test
    void duenoChangesEstacionForChef() throws Exception {
        Usuario chef = TestUsuarios.staff("Mario", Rol.CHEF, "hash", true);
        usuarioRepository.save(chef);

        mockMvc.perform(patch("/api/usuarios/" + chef.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estacion\":\"P2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estacion").value("P2"));
    }

    @Test
    void changingEstacionForNonChefIsRejected() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estacion\":\"P1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duenoRegeneratesPinAndOldPinStopsWorking() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("1111"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"pin\":\"2222\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"1111\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"2222\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void settingPinOnDuenoIsRejected() throws Exception {
        Usuario otroDueno = TestUsuarios.dueno("otro-dueno-edit", "hash", true);
        usuarioRepository.save(otroDueno);

        mockMvc.perform(patch("/api/usuarios/" + otroDueno.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUsuarioIdReturns404() throws Exception {
        mockMvc.perform(patch("/api/usuarios/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonDuenoCannotEditUsuarios() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotEditUsuarios() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

}
