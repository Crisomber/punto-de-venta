package com.posrestaurante.backend.auth;

import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.usuario.Rol;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CambiarPasswordControllerTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    private String duenoTokenConPassword(String username, String password) {
        Usuario dueno = TestUsuarios.dueno(username, passwordEncoder.encode(password), true);
        usuarioRepository.save(dueno);
        return jwtService.generateToken(dueno.getId(), username, Rol.DUENO);
    }

    private String duenoTokenConPassword(String password) {
        return duenoTokenConPassword("otro-dueno", password);
    }

    @Test
    void duenoCambiaSuPassword() throws Exception {
        String token = duenoTokenConPassword("segundo-dueno", "vieja123");

        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"vieja123\",\"passwordNueva\":\"nueva456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"segundo-dueno\",\"password\":\"nueva456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordActualIncorrectaFalla401() throws Exception {
        String token = duenoTokenConPassword("vieja123");

        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"mal\",\"passwordNueva\":\"nueva456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordNuevaVaciaFalla400() throws Exception {
        String token = duenoTokenConPassword("vieja123");

        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"vieja123\",\"passwordNueva\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonDuenoCannotUseEndpoint() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .header("Authorization", "Bearer " + tokenFor(Rol.MESERO))
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"x\",\"passwordNueva\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotUseEndpoint() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"passwordActual\":\"x\",\"passwordNueva\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

}
