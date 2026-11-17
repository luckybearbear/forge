package io.github.luckybearbear.forge.demo.mold.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** 模具仓库：所有读写经 Hibernate 判别式多租户自动按当前租户过滤（spec §6.1）。 */
public interface MoldRepository extends JpaRepository<MoldJpaEntity, String> {}
