package com.shopeasy.service;

import com.shopeasy.config.DataInitializer;
import com.shopeasy.dto.request.*;
import com.shopeasy.exception.*;
import com.shopeasy.model.*;
import com.shopeasy.repository.*;
import com.shopeasy.security.JwtUtil;
import com.shopeasy.service.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ServiceEdgeCasesTest {
    @Test void registrationWithoutDefaultRoleFailsWithoutSavingUser() {
        AuthServiceImpl service = new AuthServiceImpl(); UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        ReflectionTestUtils.setField(service, "userRepository", users); ReflectionTestUtils.setField(service, "roleRepository", roles);
        RegisterRequest request = new RegisterRequest(); request.setEmail("new@test.com");
        assertThatThrownBy(() -> service.register(request)).isInstanceOf(ResourceNotFoundException.class);
        verify(users, never()).save(any());
    }
    @Test void loginMissingUserAfterAuthenticationFailsWithoutGeneratingToken() {
        AuthServiceImpl service = new AuthServiceImpl(); UserRepository users = mock(UserRepository.class);
        AuthenticationManager manager = mock(AuthenticationManager.class); JwtUtil jwt = mock(JwtUtil.class);
        ReflectionTestUtils.setField(service, "userRepository", users); ReflectionTestUtils.setField(service, "authenticationManager", manager);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        LoginRequest request = new LoginRequest(); request.setEmail("missing@test.com"); request.setPassword("password");
        assertThatThrownBy(() -> service.login(request)).isInstanceOf(ResourceNotFoundException.class);
        verify(manager).authenticate(any(UsernamePasswordAuthenticationToken.class)); verifyNoInteractions(jwt);
    }
    @Test void loginWithNoRolesUsesFallbackRole() {
        AuthServiceImpl service = new AuthServiceImpl(); UserRepository users = mock(UserRepository.class);
        User user = new User(); user.setEmail("user@test.com"); user.setName("User");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        JwtUtil jwt = mock(JwtUtil.class); when(jwt.generateToken(user.getEmail())).thenReturn("jwt");
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "authenticationManager", mock(AuthenticationManager.class));
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        LoginRequest request = new LoginRequest(); request.setEmail(user.getEmail());
        assertThat(service.login(request).getRole()).isEqualTo("ROLE_USER");
    }
    @Test void cartAndCheckoutRejectMissingUserBeforeAnyWrites() {
        UserRepository users = mock(UserRepository.class); CartItemRepository carts = mock(CartItemRepository.class);
        ProductRepository products = mock(ProductRepository.class); OrderRepository orders = mock(OrderRepository.class);
        CartServiceImpl cart = new CartServiceImpl(); OrderServiceImpl order = new OrderServiceImpl();
        for (Object service : List.of(cart, order)) {
            ReflectionTestUtils.setField(service, "userRepository", users);
            ReflectionTestUtils.setField(service, "cartItemRepository", carts);
            ReflectionTestUtils.setField(service, "productRepository", products);
        }
        ReflectionTestUtils.setField(order, "orderRepository", orders);
        assertThatThrownBy(() -> cart.addItem("missing", new CartItemRequest())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> order.createOrder("missing")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(carts, products, orders);
    }
    @Test void productWithoutCategoryMapsNullableCategoryFields() {
        ProductRepository products = mock(ProductRepository.class); Product product = new Product(); product.setId(7L); product.setName("Uncategorized");
        when(products.findById(7L)).thenReturn(Optional.of(product));
        ProductServiceImpl service = new ProductServiceImpl(); ReflectionTestUtils.setField(service, "productRepository", products);
        var result = service.getById(7L);
        assertThat(result.getName()).isEqualTo("Uncategorized"); assertThat(result.getCategoryId()).isNull(); assertThat(result.getCategoryName()).isNull();
    }
    @Test void initializerCreatesBothRolesAndEncodedAdmin() {
        RoleRepository roles = mock(RoleRepository.class); UserRepository users = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class); when(encoder.encode(anyString())).thenReturn("encoded");
        when(roles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DataInitializer initializer = initializer(roles, users, encoder); initializer.run();
        verify(roles).save(new Role(null, "ROLE_ADMIN")); verify(roles).save(new Role(null, "ROLE_USER"));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class); verify(users).save(captor.capture());
        User admin = captor.getValue(); assertThat(admin.getEmail()).isEqualTo("admin@shopeasy.com");
        assertThat(admin.getPassword()).isEqualTo("encoded"); assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getRoles()).extracting(Role::getName).containsExactly("ROLE_ADMIN");
    }
    @Test void initializerDoesNotOverwriteExistingRolesOrAdmin() {
        RoleRepository roles = mock(RoleRepository.class); UserRepository users = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(roles.findByName("ROLE_ADMIN")).thenReturn(Optional.of(new Role(1L, "ROLE_ADMIN")));
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(new Role(2L, "ROLE_USER")));
        when(users.existsByEmail("admin@shopeasy.com")).thenReturn(true);
        initializer(roles, users, encoder).run();
        verify(roles, never()).save(any()); verify(users, never()).save(any()); verifyNoInteractions(encoder);
    }
    DataInitializer initializer(RoleRepository roles, UserRepository users, BCryptPasswordEncoder encoder) {
        DataInitializer initializer = new DataInitializer(); ReflectionTestUtils.setField(initializer, "roleRepository", roles);
        ReflectionTestUtils.setField(initializer, "userRepository", users); ReflectionTestUtils.setField(initializer, "passwordEncoder", encoder);
        return initializer;
    }
    @Test void genericErrorHandlerReturns500WithErrorBody() {
        var response = new GlobalExceptionHandler().handleGeneral(new RuntimeException("test failure"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("error", "Error interno del servidor: test failure");
    }
}
