package com.bizmetrics.platform.identity.account.infrastructure;

import com.bizmetrics.platform.identity.account.application.UserDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUserDirectory implements UserDirectory {
    private final UserDirectoryMapper mapper;

    public MyBatisUserDirectory(UserDirectoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Entry> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public Optional<Entry> findBootstrapOwner() {
        return Optional.ofNullable(mapper.findBootstrapOwner());
    }

    @Override
    public List<Entry> findAll() {
        return mapper.findAll();
    }
}
