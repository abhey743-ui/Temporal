
@ActivityInterface
public interface CreateTripInterface {
    @ActivityMethod
    public void createTrip(MakeTripDto makeTripDto);
    @ActivityMethod
    public void bookHotel(MakeTripDto makeTripDto);
    @ActivityMethod
    public void bookTransportation(MakeTripDto makeTripDto);

    void cancelTrip(MakeTripDto makeTripDto);
    void cancelHotel(MakeTripDto makeTripDto);
    void cancelTransportation(MakeTripDto makeTripDto);
}
