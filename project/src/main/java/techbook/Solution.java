package techbook;

import techbook.business.*;
import techbook.data.DBConnector;
import techbook.data.PostgreSQLErrorCodes;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import static techbook.business.ReturnValue.*;

public class Solution {

    static Connection connection;
    static PreparedStatement statement;
    static ResultSet resultSet;
    static void closeAll()
    {
        try { resultSet.close(); } catch(Exception e) { }
        try { statement.close(); } catch(Exception e) { }
        try { connection.close(); } catch(Exception e) { }
    }

    /**
     Adds a student to the database. The student should join to the faculty’s group
     input: student to be added
     output: ReturnValue with the following conditions:
     * OK in case of success
     * BAD_PARAMS in case of illegal parameters
     * ALREADY_EXISTS if student already exists
     * ERROR in case of database error
     */
    public static ReturnValue addStudent(Student student)
    {
        //connect
        connection = DBConnector.getConnection();

        try {
            //add to students
            statement = connection.prepareStatement(
                    "INSERT INTO Students VALUES (?, ?, ?)");
            statement.setInt(1, student.getId());
            statement.setString(2, student.getName());
            statement.setString(3, student.getFaculty());
            statement.execute();
        }
        catch (SQLException e) {
            if(Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.UNIQUE_VIOLATION.getValue()) {
                closeAll();
                return ALREADY_EXISTS;
            }
            else if(Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.NOT_NULL_VIOLATION.getValue()
                    || Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.CHECK_VIOLATION.getValue()) {
                closeAll();
                return BAD_PARAMS;
            }
            closeAll();
            return ERROR;
        }

        try{
            //add student to faculty's group
            statement = connection.prepareStatement("INSERT INTO GroupMembership VALUES (?, ?)");
            statement.setInt(1,student.getId());
            statement.setString(2,student.getFaculty());
            statement.execute();
        }
        catch (SQLException e)
        {
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     Deletes a student from the database
     Deleting a student will cause him\her to leave their group, delete their posts and likes history, and friendships
     input: student
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if student does not exist
     * ERROR in case of database error

     */
    public static ReturnValue deleteStudent(Integer studentId)
    {
        //connect
        connection = DBConnector.getConnection();

        try{
            statement = connection.prepareStatement("DELETE FROM Students WHERE StudentID = ?");
            statement.setInt(1,studentId);
            int affectedRows = statement.executeUpdate();
            if(affectedRows==0) {
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e) {
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }


    /**
     *
     Returns the student profile by the given id
     input: student id
     output: The student profile in case the student exists. BadStudent otherwise

     */
    public static Student getStudentProfile(Integer studentId)
    {
        //connect
        connection = DBConnector.getConnection();

        try
        {
            statement = connection.prepareStatement("SELECT * FROM Students WHERE StudentId = ?");
            statement.setInt(1,studentId);

            resultSet = statement.executeQuery();
            Student ret_val = new Student();
            resultSet.next();
            ret_val.setId(studentId);
            ret_val.setName(resultSet.getString(2));
            ret_val.setFaculty(resultSet.getString(3));

            closeAll();
            return ret_val;
        }
        catch (SQLException e)
        {
            closeAll();
            return Student.badStudent();
        }

    }

    /**
     Updates a student faculty to the new given value.
     The student should join the group of the new faculty, and stay in the old faculty’s group.
     input: updated student
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if student does not exist
     * BAD_PARAMS in case of illegal parameters
     * ERROR in case of database error
     */
    public static ReturnValue updateStudentFaculty(Student student){

        //connect
        connection = DBConnector.getConnection();

        //ALREADY_EXISTS if the student is current member of the faculty
        try {
            statement = connection.prepareStatement("SELECT * FROM Students WHERE StudentId = ? AND Faculty  = ?");
            statement.setInt(1, student.getId());
            statement.setString(2, student.getFaculty());

            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                closeAll();
                return ALREADY_EXISTS;
            }
        }
        catch (SQLException e) {
            closeAll();
            return ERROR;
        }

        //update faculty
        try{
            statement = connection.prepareStatement("UPDATE Students SET Faculty = ? WHERE StudentId = ?");
            statement.setString(1,student.getFaculty());
            statement.setInt(2,student.getId());

            int affectedRows = statement.executeUpdate();
            //student does not exist
            if(affectedRows == 0)
            {
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e) {
            if( Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.CHECK_VIOLATION.getValue() ||
                    Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.NOT_NULL_VIOLATION.getValue())
            {
                closeAll();
                return BAD_PARAMS;
            }

            closeAll();
            return ERROR;
        }

        //try to add to group (it's fine if already in this group)
        try
        {
            statement = connection.prepareStatement("INSERT INTO GroupMembership VALUES (?, ?)");
            statement.setInt(1,student.getId());
            statement.setString(2,student.getFaculty());
            statement.execute();
        }
        catch (SQLException e) {
            //if not a unique violation
            if( Integer.valueOf(e.getSQLState())!= PostgreSQLErrorCodes.UNIQUE_VIOLATION.getValue())
            {
                closeAll();
                return ERROR;
            }
        }

        closeAll();
        return OK;
    }

    /**
     Adds a post to the database, and adds it to the relevant group if  groupName is given (i.e., it is not null)
     When a student can write a post in a group only if he\she is one of its members
     input: post to be posted
     output: ReturnValue with the following conditions:
     * OK in case of success
     * BAD_PARAMS in case of illegal parameters
     * NOT_EXISTS if student is not a member in the group
     * ALREADY_EXISTS if post already exists
     * ERROR in case of database error
     */
    public static ReturnValue addPost(Post post, String groupName)
    {
        //connect
        connection = DBConnector.getConnection();

        //first check that the student is a member of the given group
        if(groupName != null)
        {
            try
            {
                statement = connection.prepareStatement("SELECT 1 FROM GroupMembership WHERE StudentID = "+post.getAuthor()+" AND GroupName = '"+groupName+"' ");
                resultSet = statement.executeQuery();
                if(!resultSet.next())
                {
                    closeAll();
                    return NOT_EXISTS;
                }

            }
            catch (SQLException e)
            {
                closeAll();
                return ERROR;
            }
        }

        //add the post
        try
        {
            String quotedGroup = (groupName == null) ? null : "'"+groupName+"'";
            String quotedText = (post.getText() == null) ? null : "'"+post.getText()+"'";
            String quotedTime = (post.getTimeStamp() == null) ? null : "'"+post.getTimeStamp()+"'";
            statement = connection.prepareStatement("INSERT INTO Posts VALUES ("
                    + post.getId() + ","
                    + post.getAuthor() + ","
                    + quotedGroup + ","
                    + quotedText + ","
                    + quotedTime + ")"
            );
            statement.execute();

        }
        catch (SQLException e)
        {
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.CHECK_VIOLATION.getValue())
            {
                closeAll();
                return BAD_PARAMS;
            }
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.NOT_NULL_VIOLATION.getValue())
            {
                closeAll();
                return BAD_PARAMS;
            }
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.FOREIGN_KEY_VIOLATION.getValue())
            {
                //student does not exist
                closeAll();
                return NOT_EXISTS;
            }
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.UNIQUE_VIOLATION.getValue())
            {
                closeAll();
                return ALREADY_EXISTS;
            }

            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     Deletes a post from the database
     input: post to be deleted
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if post does not exist
     * ERROR in case of database error

     */
    public static ReturnValue deletePost(Integer postId)
    {
        //connect
        connection = DBConnector.getConnection();

        //delete the post
        try
        {
            statement = connection.prepareStatement("DELETE FROM Posts WHERE PostID = " + postId );
            int count = statement.executeUpdate();
            //post does not exist
            if( count == 0)
            {
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e)
        {
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     *
     returns the post by given id
     input: post id
     output: Post if the post exists. BadPost otherwise

     */
    public static Post getPost(Integer postId)
    {
        //connect
        connection = DBConnector.getConnection();

        Post post = new Post();
        try
        {
            //get post
            statement = connection.prepareStatement("SELECT * FROM Posts WHERE PostID = " + postId );
            resultSet = statement.executeQuery();

            //post does not exist
            if(!resultSet.next())
            {
                closeAll();
                return Post.badPost();
            }

            post.setId(postId);
            post.setAuthor(resultSet.getInt("AuthorID"));
            post.setText(resultSet.getString("Text"));
            post.setTimeStamp(resultSet.getTimestamp("Time"));

            //get post likes
            statement = connection.prepareStatement("SELECT Likes FROM PostLikes WHERE PostID = " + postId );
            resultSet = statement.executeQuery();

            resultSet.next();
            post.setLikes(resultSet.getInt("Likes"));
        }
        catch (SQLException e)
        {
            closeAll();
            return Post.badPost();
        }

        closeAll();
        return post;
    }

    /**
     Updates a post’s text
     input: updated post
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if post does not exist
     * BAD_PARAMS in case of illegal parameters
     * ERROR in case of database error


     */
    public static ReturnValue updatePost(Post post)
    {
        //connect
        connection = DBConnector.getConnection();

        try
        {
            String quoted = (post.getText() == null) ? null : "'"+post.getText()+"'";
            statement = connection.prepareStatement("UPDATE Posts SET Text = "+quoted+" WHERE PostID = " + post.getId() );
            int count = statement.executeUpdate();
            //post does not exist
            if( count == 0)
            {
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e)
        {
            //null text
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.NOT_NULL_VIOLATION.getValue())
            {
                closeAll();
                return BAD_PARAMS;
            }
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }


    /**
     Establishes a friendship relationship between two different students
     input: student id 1, student id 2
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if one or two of the students do not exist
     * ALREADY_EXISTS if the students are already friends
     * BAD_PARAMS in case of illegal parameters
     * ERROR in case of database error

     */

    public static ReturnValue makeAsFriends(Integer studentId1, Integer studentId2)
    {
        //connect
        connection = DBConnector.getConnection();

        try
        {
            statement = connection.prepareStatement("INSERT INTO Friends VALUES ("
                    + studentId1 +","
                    + studentId2 +")"
            );
            statement.execute();
            statement = connection.prepareStatement("INSERT INTO Friends VALUES ("
                    + studentId2 +","
                    + studentId1 +")"
            );
            statement.execute();
        }
        catch (SQLException e)
        {
            //student does not exist
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.FOREIGN_KEY_VIOLATION.getValue())
            {
                closeAll();
                return NOT_EXISTS;
            }
            //friendship already exists
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.UNIQUE_VIOLATION.getValue())
            {
                closeAll();
                return ALREADY_EXISTS;
            }
            //bad param
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.CHECK_VIOLATION.getValue())
            {
                closeAll();
                return BAD_PARAMS;
            }
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }


    /**
     Removes a friendship connection of two students
     input: student id 1, student id 2
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if one or two of the students do not exist,  or they are not labeled as friends
     * ERROR in case of database error

     */
    public static ReturnValue makeAsNotFriends  (Integer studentId1, Integer studentId2)
    {
        //connect
        connection = DBConnector.getConnection();

        try
        {
            statement = connection.prepareStatement("DELETE FROM Friends WHERE "
                    + "(StudentID_A = "+studentId1+" AND StudentID_B = "+studentId2+" )"
                    + "OR (StudentID_A = "+studentId2+" AND StudentID_B = "+studentId1+" )"
            );
            int count = statement.executeUpdate();
            if( count == 0)
            {
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e)
        {
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     Marks a post as liked by a student
     input: student id, liked post id
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if student or post do not exist
     *ALREADY_EXISTS if the student is already likes the post
     * ERROR in case of database error

     */
    public static ReturnValue likePost(Integer studentId, Integer postId)
    {
        //connect
        connection = DBConnector.getConnection();

        //check that student is a member of the post's group (if it has one)
        try
        {
            //get post's group
            statement = connection.prepareStatement("SELECT GroupName FROM Posts WHERE PostID = " + postId );
            resultSet = statement.executeQuery();
            //if post does not exist
            if(!resultSet.next())
            {
                closeAll();
                return NOT_EXISTS;
            }
            String groupName = resultSet.getString(1);

            if(groupName != null)
            {
                //check that student is a member
                statement = connection.prepareStatement("SELECT * FROM GroupMembership WHERE " +
                        "StudentID = " + studentId + " AND GroupName = '" + groupName + "'");
                resultSet = statement.executeQuery();
                //if not a member
                if(!resultSet.next())
                {
                    closeAll();
                    return NOT_EXISTS;
                }
            }
        }
        catch (SQLException e) {
            closeAll();
            return ERROR;
        }

        //insert like
        try
        {
            statement = connection.prepareStatement("INSERT INTO Likes VALUES ("
                    + studentId +","
                    + postId +")"
            );
            statement.execute();
        }
        catch (SQLException e)
        {
            //student does not exist
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.FOREIGN_KEY_VIOLATION.getValue())
            {
                closeAll();
                return NOT_EXISTS;
            }
            //like already exists
            if(Integer.valueOf(e.getSQLState()) == PostgreSQLErrorCodes.UNIQUE_VIOLATION.getValue())
            {
                closeAll();
                return ALREADY_EXISTS;
            }
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     Removes the like marking of a post by the student
     input: student id, unliked post id
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if student or post do not exist,  or the student did not like the post
     * ERROR in case of database error

     */
    public static ReturnValue unlikePost(Integer studentId, Integer postId)
    {
        //connect
        connection = DBConnector.getConnection();

        try
        {
            statement = connection.prepareStatement("DELETE FROM Likes WHERE "
                    + "StudentID = "+studentId+" AND PostID = "+postId
            );
            int count = statement.executeUpdate();
            if( count == 0)
            {
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e)
        {
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     *
     Adds a student to a group
     input: id of student to be added, the group name the student is added to
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if the student does not exist
     * ALREADY_EXISTS if the student are already in that group
     * ERROR in case of database error
     */
    public static ReturnValue joinGroup(Integer studentId, String groupName)
    {
        //connect
        connection = DBConnector.getConnection();

        try {
            statement = connection.prepareStatement(
                    "SELECT * FROM Students WHERE StudentID = ?");
            statement.setInt(1,studentId);
            resultSet = statement.executeQuery();
            if (!(resultSet.next())) {//check if student exists
                closeAll();
                return NOT_EXISTS;
            }

            statement = connection.prepareStatement(
                    "SELECT * FROM GroupMembership "
                            + "WHERE StudentID  = ? AND GroupName   = ?");
            statement.setInt(1, studentId);
            statement.setString(2, groupName);
            resultSet = statement.executeQuery();

            if (resultSet.next()) {//check if student with the same group exists
                closeAll();
                return ALREADY_EXISTS;
            }
        }
        catch (SQLException e) {
            if( Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.CHECK_VIOLATION.getValue()) {
                closeAll();
                return BAD_PARAMS;
            }

            closeAll();
            return ERROR;
        }

        try{
            statement = connection.prepareStatement(
                    "INSERT INTO GroupMembership"
                            + " VALUES (?, ?)");
            statement.setInt(1,studentId);
            statement.setString(2,groupName);
            statement.execute();

        }
        catch (SQLException e) {
            if( Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.CHECK_VIOLATION.getValue() ||
                    Integer.valueOf(e.getSQLState())== PostgreSQLErrorCodes.NOT_NULL_VIOLATION.getValue()) {
                closeAll();
                return BAD_PARAMS;
            }

            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;
    }

    /**
     *
     Removes a student from a group
     input: student id 1, student id 2
     output: ReturnValue with the following conditions:
     * OK in case of success
     * NOT_EXISTS if the student is not a member of the group
     * ERROR in case of database error
     */
    public static ReturnValue leaveGroup(Integer studentId,String groupName)
    {
        //connect
        connection = DBConnector.getConnection();

        try {
            statement = connection.prepareStatement(
                    "DELETE FROM GroupMembership "
                            + "WHERE StudentID  = ? AND GroupName = ?");
            statement.setInt(1, studentId);
            statement.setString(2, groupName);
            int res = statement.executeUpdate();

            if (res==0) {//check if student with the same group exists
                closeAll();
                return NOT_EXISTS;
            }
        }
        catch (SQLException e) {
            closeAll();
            return ERROR;
        }

        closeAll();
        return OK;

    }


    /**
     *
     Gets a list of personal posts posted by a student and his\her friends. Feed should be ordered by date and likes, both in descending order.
     input: student id
     output: Feed the containing the relevant posts. In case of an error, return an empty feed
     */
    public static Feed getStudentFeed(Integer id)
    {
        //connect
        connection = DBConnector.getConnection();

        Feed ret_feed = new Feed();
        try{
            statement = connection.prepareStatement(
                    "SELECT Posts.PostID, Posts.AuthorID, Posts.Text, Posts.Time, PostLikes.Likes " +
                            "FROM Posts, PostLikes " +
                            "WHERE Posts.GroupName IS NULL " +
                            "AND (Posts.AuthorID = ? OR Posts.AuthorID IN (SELECT StudentID_A FROM Friends WHERE StudentID_B = ?)) " +
                            "AND Posts.PostID = PostLikes.postID " +
                            "ORDER BY Posts.Time DESC, PostLikes.Likes DESC ");
            statement.setInt(1,id);
            statement.setInt(2,id);
            resultSet = statement.executeQuery();
            while (resultSet.next()){
                Post post_feed = new Post();
                post_feed.setId(resultSet.getInt(1));
                post_feed.setAuthor(resultSet.getInt(2));
                post_feed.setText(resultSet.getString(3));
                post_feed.setTimeStamp(resultSet.getTimestamp(4));
                post_feed.setLikes(resultSet.getInt(5));
                ret_feed.add(post_feed);
            }

            closeAll();
            return ret_feed;
        }
        catch (SQLException e){
            closeAll();
            return new Feed();
        }
    }

    /**
     *
     Gets a list of posts posted in a group. Feed should be ordered by date and likes, both in descending order.
     input: group
     output: Feed the containing the relevant posts. In case of an error, return an empty feed
     */
    public static Feed getGroupFeed(String groupName)
    {
        //connect
        connection = DBConnector.getConnection();

        Feed ret_feed = new Feed();
        try{
            statement=connection.prepareStatement(
                    "SELECT Posts.PostID, Posts.AuthorID, Posts.Text, Posts.Time, PostLikes.Likes " +
                            " FROM Posts, PostLikes WHERE Posts.GroupName = " + groupName +
                            " AND PostLikes.PostID = Posts.PostID" +
                            " ORDER BY Posts.Time DESC, PostLikes.Likes DESC ");
            resultSet = statement.executeQuery();
            while (resultSet.next()){
                Post post_feed = new Post();
                post_feed.setId(resultSet.getInt(1));
                post_feed.setAuthor(resultSet.getInt(2));
                post_feed.setText(resultSet.getString(3));
                post_feed.setTimeStamp(resultSet.getTimestamp(4));
                ret_feed.add(post_feed);
            }

            closeAll();
            return ret_feed;
        }
        catch (SQLException e){
            closeAll();
            return new Feed();
        }
    }

    /**
     Gets a list of students that the given student may know.
     Denote the given the student by s. The returned list should consist of every student x in the database that holds the following:
     - s ≠ x.
     - s and x are not friends.
     - There exists a student y such that y ≠ s, y ≠ x, s and y are friends, and y and x are friends.
     - There exists a group such that both s and x are members of.
     input: student
     output: an ArrayList containing the students. In case of an error, return an empty ArrayList

     */
    public static ArrayList<Student> getPeopleYouMayKnowList(Integer studentId)
    {
        //connect
        connection = DBConnector.getConnection();

        try{
            statement=connection.prepareStatement(
                    "SELECT * FROM Students WHERE StudentID  in(\n" +
                            "SELECT f2.StudentID_B\n" +
                            "From Friends f1\n" +
                            "INNER JOIN Friends f2 ON f1.StudentID_B = f2.StudentID_A\n" +
                            "INNER JOIN GroupMembership ON f2.StudentID_B = GroupMembership.StudentID\n" +
                            "WHERE f1.StudentID_A <> f2.StudentID_B AND f1.StudentID_A = ? AND\n" +
                            "GroupMembership.GroupName IN (SELECT GroupName FROM GroupMembership WHERE StudentID = ?) AND\n" +
                            "f2.studentid_b NOT IN (SELECT friends.studentid_b FROM friends WHERE friends.studentid_a = ?))");
            statement.setInt(1,studentId);
            statement.setInt(2,studentId);
            statement.setInt(3,studentId);
            ResultSet res = statement.executeQuery();
            ArrayList<Student> ret_val = new ArrayList<Student>();
            while(res.next()) {
                Student ret_student = new Student();
                ret_student.setId(res.getInt(1));
                ret_student.setName(res.getString(2));
                ret_student.setFaculty(res.getString(3));
                ret_val.add(ret_student);
            }

            closeAll();
            return ret_val;
        }
        catch (SQLException e){
            closeAll();
            return new ArrayList<Student>();
        }
    }


    /**
     Returns a list of student id pairs (s1, s2) such that the degrees of separation (definition follows)
     between s1 and s2 is at least 5.
     To define the notion of degrees of separation let us consider a graph, called the friendship graph,
     where its nodes are the students in the database, and there is an edge between two students iff they are friends.
     The degrees of separation between students s1 and s2 is defined as the length of the shortest path
     connecting s1 and s2 in the undirected friendship graph.
     input: none
     output: an ArrayList containing the student pairs. In case of an error, return an empty ArrayList

     */
    public static ArrayList<StudentIdPair> getRemotelyConnectedPairs()
    {
        closeAll();
        return null;
    }


    //tables
    //****************************************************************************************************
    public static void createTables()
    {

        //connect
        connection = DBConnector.getConnection();

        try
        {
            statement = connection.prepareStatement(
                    "CREATE TABLE Students("
                            +"StudentID INTEGER,"
                            +"Name VARCHAR(100) NOT NULL,"
                            +"Faculty VARCHAR(100) NOT NULL,"

                            +"PRIMARY KEY (StudentID),"
                            +"CHECK (StudentID > 0)"
                            +")");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement(
                    "CREATE TABLE GroupMembership("
                            +"StudentID INTEGER,"
                            +"GroupName VARCHAR(100) NOT NULL,"

                            +"PRIMARY KEY (StudentID, GroupName),"
                            +"FOREIGN KEY (StudentID) REFERENCES Students(StudentID) ON DELETE CASCADE"
                            +")");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement(
                    "CREATE TABLE Posts("
                            +"PostID INTEGER,"
                            +"AuthorID INTEGER,"
                            +"GroupName VARCHAR(100),"
                            +"Text TEXT NOT NULL,"
                            +"Time TIMESTAMP NOT NULL,"

                            +"PRIMARY KEY (PostID),"
                            +"CHECK (PostID > 0),"
                            +"FOREIGN KEY (AuthorID) REFERENCES Students(StudentID) ON DELETE CASCADE"
                            +")");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement(
                    "CREATE TABLE Likes("
                            +"StudentID INTEGER,"
                            +"PostID INTEGER,"

                            +"PRIMARY KEY (StudentID, PostID),"
                            +"FOREIGN KEY (StudentID) REFERENCES Students(StudentID) ON DELETE CASCADE,"
                            +"FOREIGN KEY (PostID) REFERENCES Posts(PostID) ON DELETE CASCADE"
                            +")");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement(
                    "CREATE TABLE Friends("
                            +"StudentID_A INTEGER,"
                            +"StudentID_B INTEGER,"

                            +"PRIMARY KEY (StudentID_A, StudentID_B),"
                            +"CHECK (StudentID_A != StudentID_B),"
                            +"FOREIGN KEY (StudentID_A) REFERENCES Students(StudentID) ON DELETE CASCADE,"
                            +"FOREIGN KEY (StudentID_B) REFERENCES Students(StudentID) ON DELETE CASCADE"
                            +")");
            statement.execute();
        }
        catch (SQLException e) {}

        //views
        //**********************************************
        try
        {
            statement = connection.prepareStatement(
                    "CREATE VIEW PostLikes AS "
                            +"SELECT PostID, COUNT(PostID)-1 Likes " +
                            "FROM ((SELECT PostID FROM Posts) UNION ALL (SELECT PostID FROM Likes)) AS Uni " +
                            "GROUP BY PostID"
            );
            statement.execute();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }


        closeAll();
    }


    public static void clearTables()
    {
        //connect
        connection = DBConnector.getConnection();

        //opposite order of create tables

        try
        {
            statement = connection.prepareStatement("DELETE FROM Friends");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DELETE FROM Likes");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DELETE FROM Posts");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DELETE FROM GroupMembership");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DELETE FROM Students");
            statement.execute();
        }
        catch (SQLException e) {}

        closeAll();
    }



    public static void dropTables()
    {
        //connect
        connection = DBConnector.getConnection();

        //opposite order of create tables

        try
        {
            statement = connection.prepareStatement("DROP VIEW PostLikes");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DROP TABLE Friends");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DROP TABLE Likes");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DROP TABLE Posts");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DROP TABLE GroupMembership");
            statement.execute();
        }
        catch (SQLException e) {}

        try
        {
            statement = connection.prepareStatement("DROP TABLE Students");
            statement.execute();
        }
        catch (SQLException e) {}

        closeAll();
    }

 }

