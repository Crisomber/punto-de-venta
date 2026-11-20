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

class ActivarUsuarioControllerTest extends AbstractIntegrationTest {

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
    void deactivatingUsuarioBlocksTheirNextPinLogin() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"4821\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reactivatingUsuarioAllowsLoginAgain() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, passwordEncoder.encode("4821"), false);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(post("/api/auth/login/pin")
                        .contentType(APPLICATION_JSON)
                        .content("{\"usuarioId\":\"" + mesero.getId() + "\",\"pin\":\"4821\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void activoFieldIsRequired() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUsuarioIdReturns404() throws Exception {
        mockMvc.perform(patch("/api/usuarios/" + UUID.randomUUID() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonDuenoCannotToggleActivo() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId() + "/activo")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CHEF))
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotToggleActivo() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        usuarioRepository.save(mesero);

        mockMvc.perform(patch("/api/usuarios/" + mesero.getId() + "/activo")
                        .contentType(APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isUnauthorized());
    }

}
