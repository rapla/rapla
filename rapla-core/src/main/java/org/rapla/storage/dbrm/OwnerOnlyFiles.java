package org.rapla.storage.dbrm;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owner-only creation and verification of the token file and its directory (PRD 131 D4).
 * POSIX: mode bits, no group/other bit allowed. Windows: ACL; besides the owner, only the
 * principals that can read the user's profile directory (profile default: SYSTEM,
 * Administrators — localized, so taken from the profile ACL instead of names) may read.
 */
final class OwnerOnlyFiles
{
    private static final Set<PosixFilePermission> DIR = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE = PosixFilePermissions.fromString("rw-------");

    private OwnerOnlyFiles() {}

    /** Creates the missing directories and the file with owner-only attributes, as one step of the creation. */
    static void create(Path file) throws IOException
    {
        Path dir = file.getParent();
        if (dir != null && !Files.isDirectory(dir))
        {
            Files.createDirectories(dir, ownerOnly(dir, true));
        }
        if (!Files.exists(file))
        {
            try
            {
                Files.createFile(file, ownerOnly(file, false));
            }
            catch (FileAlreadyExistsException created)
            {
                // raced with another writer; verified by the caller
            }
        }
    }

    /**
     * Tightens an existing directory that belongs to the current user, then checks directory and file
     * (where they exist): owned by the current user, no access for anyone else.
     * Returns the reason when not, else null. Never throws.
     */
    static String secure(Path file)
    {
        try
        {
            FileSystem fs = file.getFileSystem();
            boolean posix = fs.supportedFileAttributeViews().contains("posix");
            if (!posix && !fs.supportedFileAttributeViews().contains("acl"))
            {
                return "permissions cannot be verified on this file system";
            }
            UserPrincipal me = currentUser(fs);
            Path dir = file.getParent();
            if (dir != null && Files.isDirectory(dir))
            {
                String problem = check(dir, me, posix);
                if (problem != null && tighten(dir, me, posix)) problem = check(dir, me, posix);
                if (problem != null) return problem;
            }
            return Files.exists(file) ? check(file, me, posix) : null;
        }
        catch (Exception e)
        {
            return "cannot verify permissions: " + e.getMessage();
        }
    }

    private static UserPrincipal currentUser(FileSystem fs) throws IOException
    {
        return fs.getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
    }

    private static FileAttribute<?> ownerOnly(Path path, boolean directory) throws IOException
    {
        FileSystem fs = path.getFileSystem();
        if (fs.supportedFileAttributeViews().contains("posix"))
        {
            return PosixFilePermissions.asFileAttribute(directory ? DIR : FILE);
        }
        List<AclEntry> acl = ownerOnlyAcl(currentUser(fs));
        return new FileAttribute<List<AclEntry>>()
        {
            @Override public String name() { return "acl:acl"; }
            @Override public List<AclEntry> value() { return acl; }
        };
    }

    private static List<AclEntry> ownerOnlyAcl(UserPrincipal me)
    {
        return List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(me)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
    }

    private static boolean tighten(Path dir, UserPrincipal me, boolean posix) throws IOException
    {
        if (posix)
        {
            if (!Files.readAttributes(dir, PosixFileAttributes.class).owner().equals(me)) return false;
            Files.setPosixFilePermissions(dir, DIR);
            return true;
        }
        AclFileAttributeView view = Files.getFileAttributeView(dir, AclFileAttributeView.class);
        if (!view.getOwner().equals(me)) return false;
        view.setAcl(ownerOnlyAcl(me));
        return true;
    }

    private static String check(Path path, UserPrincipal me, boolean posix) throws IOException
    {
        if (posix)
        {
            PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class);
            if (!attrs.owner().equals(me)) return path + " belongs to " + attrs.owner().getName();
            Set<PosixFilePermission> others = new HashSet<>(attrs.permissions());
            others.removeAll(DIR);
            return others.isEmpty() ? null
                    : path + " is accessible by group/others (" + PosixFilePermissions.toString(attrs.permissions()) + ")";
        }
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (!view.getOwner().equals(me)) return path + " belongs to " + view.getOwner().getName();
        Set<UserPrincipal> accepted = new HashSet<>();
        accepted.add(me);
        AclFileAttributeView home = Files.getFileAttributeView(Path.of(System.getProperty("user.home", ".")), AclFileAttributeView.class);
        if (home != null)
        {
            for (AclEntry e : home.getAcl()) accepted.add(e.principal());
        }
        for (AclEntry e : view.getAcl())
        {
            if (e.type() == AclEntryType.ALLOW && e.permissions().contains(AclEntryPermission.READ_DATA) && !accepted.contains(e.principal()))
            {
                return path + " is readable by " + e.principal().getName();
            }
        }
        return null;
    }
}
