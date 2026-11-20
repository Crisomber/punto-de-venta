package com.posrestaurante.backend.usuario;

import com.posrestaurante.backend.security.JwtService;
import com.posrestaurante.backend.testsupport.AbstractIntegrationTest;
import com.posrestaurante.backend.testsupport.TestUsuarios;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListarUsuariosControllerTest extends AbstractIntegrationTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JwtService jwtService;

    private String tokenFor(Rol rol) {
        return jwtService.generateToken(UUID.randomUUID(), "Alguien", rol);
    }

    @Test
    void duenoListsAllUsuariosIncludingInactiveAndDueno() throws Exception {
        Usuario mesero = TestUsuarios.staff("Luis", Rol.MESERO, "hash", true);
        Usuario chefInactivo = TestUsuarios.staff("Mario", Rol.CHEF, "hash", false);
        usuarioRepository.save(mesero);
        usuarioRepository.save(chefInactivo);

        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.DUENO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Luis')]").exists())
                .andExpect(jsonPath("$[?(@.nombre == 'Mario' && @.activo == false)]").exists())
                .andExpect(jsonPath("$[?(@.username == 'admin')]").exists());
    }

    @Test
    void nonDuenoCannotListUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenFor(Rol.CAJERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotListUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

}
