package com.posrestaurante.backend.auth;

import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import com.posrestaurante.backend.usuario.Usuario;
import com.posrestaurante.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DuenoAuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void duenoLogsInWithCorrectCredentials() throws Exception {
        Usuario dueno = TestUsuarios.dueno("ana", passwordEncoder.encode("secreto123"), true);
        usuarioRepository.save(dueno);

        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ana","password":"secreto123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("DUENO"));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        Usuario dueno = TestUsuarios.dueno("ana", passwordEncoder.encode("secreto123"), true);
        usuarioRepository.save(dueno);

        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ana","password":"wrong"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"nunca-existio","password":"wrong"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInactiveDueno() throws Exception {
        Usuario dueno = TestUsuarios.dueno("ana", passwordEncoder.encode("secreto123"), false);
        usuarioRepository.save(dueno);

        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ana","password":"secreto123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededDevDuenoCanLogIn() throws Exception {
        mockMvc.perform(post("/api/auth/login/dueno")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"cambiame123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

}
