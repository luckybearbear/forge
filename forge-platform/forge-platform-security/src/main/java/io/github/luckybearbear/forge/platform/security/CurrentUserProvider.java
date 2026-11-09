package io.github.luckybearbear.forge.platform.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> currentUser();
}
